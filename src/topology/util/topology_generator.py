import random
import networkx as nx
import yaml
import os
import template
import shutil
import argparse
from ipaddress import ip_network
from yaml import Dumper


def generate_nodes_links(as_node_count: int, avg_degree: float) -> tuple[dict, list]:
    if as_node_count < 2:
        print("Node count must be greater than or equal to 2")
        return

    max_possible_degree = as_node_count - 1
    if avg_degree > max_possible_degree:
        print(f"Average degree cannot be greater than {max_possible_degree}")
        return

    if avg_degree < 1:
        print("Average degree must be greater than or equal to 1")
        return

    # # calculate edge count
    # # generate random graph
    # G = nx.gnm_random_graph(as_node_count, num_edges)
    # # ensure graph is connected
    # while not nx.is_connected(G):
    #     G = nx.gnm_random_graph(as_node_count, num_edges)

    # ensure the graph is connected (at least n-1 edges)
    num_edges = int((as_node_count * avg_degree) / 2)
    min_edges_for_connectivity = as_node_count - 1
    if num_edges < min_edges_for_connectivity:
        num_edges = min_edges_for_connectivity
    # small world graph
    G = nx.watts_strogatz_graph(as_node_count, min(4, num_edges), 0.3)
    # ensure graph is connected
    if not nx.is_connected(G):
        G = nx.barabasi_albert_graph(as_node_count, 2)

    nodes = {}
    for i in range(as_node_count):
        node_name = f"node{i+1}"

        nodes[node_name] = {
            "default_status": "UP",
            "alias": node_name,
            "node_type": "as",
        }

    # generate links list
    links = []
    for edge in G.edges():
        links.append([f"node{edge[0]+1}", f"node{edge[1]+1}"])

    return nodes, links


def generate_ip_mac(subnet, ipv6_subnet, mac_prefix, num_nodes):
    """generate ip and mac address for nodes"""

    network = ip_network(subnet)
    ipv6_network = ip_network(ipv6_subnet)

    # Subtract network, broadcast, and first address
    if num_nodes > network.num_addresses - 3:
        raise ValueError("Number of distributable IPs is less than the number of nodes")

    # generate IP addresses
    ip_gen = network.hosts()
    next(ip_gen)

    # generate IPv6 addresses
    ipv6_gen = ipv6_network.hosts()
    next(ipv6_gen)

    # generated MAC addresses
    generated_macs = set()

    # generate MAC addresses
    for _ in range(num_nodes):
        while True:
            mac_end = ":{:02x}:{:02x}:{:02x}".format(
                random.randint(0, 255), random.randint(0, 255), random.randint(0, 255)
            )
            mac = mac_prefix + mac_end
            if mac not in generated_macs:
                generated_macs.add(mac)
                break

        ip = str(next(ip_gen))
        ipv6 = str(next(ipv6_gen))
        yield ip, ipv6, mac


def generate_link_subnet(base_subnet, link_index, prefix_len=30):
    """generate link subnet for physical link"""
    base_network = ip_network(base_subnet)
    base_prefix_len = base_network.prefixlen
    available_subnets = 2 ** (prefix_len - base_prefix_len)

    if link_index >= available_subnets:
        raise ValueError(
            f"Link index {link_index + 1} exceeds the available IPv4 subnet count {available_subnets}"
        )

    subnet_size = 2 ** (32 - prefix_len)
    new_network = base_network.network_address + (link_index * subnet_size)

    return f"{new_network}/{prefix_len}"


def generate_link_ipv6_subnet(base_subnet, link_index, prefix_len=126):
    """generate ipv6 link subnet for physical link"""
    base_network = ip_network(base_subnet)
    base_prefix_len = base_network.prefixlen
    available_subnets = 2 ** (prefix_len - base_prefix_len)

    if link_index >= available_subnets:
        raise ValueError(
            f"Link index {link_index + 1} exceeds the available IPv6 subnet count {available_subnets}"
        )

    subnet_size = 2 ** (128 - prefix_len)
    new_network = base_network.network_address + (link_index * subnet_size)

    return f"{new_network}/{prefix_len}"


def prepare_bgpd_conf(node_name, neighbors, node_info, ipv6=True):
    """
    Unified function to prepare BGP configuration for a node.
    """
    # get asn from node_info
    node_asn = node_info[node_name]["asn"] if isinstance(node_info, dict) else node_info

    lines = [
        f"hostname {node_name}",
        f"router bgp {node_asn}",
        f"    no bgp ebgp-requires-policy",
        f"    no bgp network import-check",
    ]

    # choose address family
    af_name = "ipv6" if ipv6 else "ipv4"
    ip_key = "ipv6" if ipv6 else "ip"

    # configure each neighbor
    for neighbor in neighbors:
        # get neighbor ip and asn
        if isinstance(neighbor, dict):
            # physical link mode: neighbor is a dict with complete info
            neighbor_ip = neighbor[ip_key]
            neighbor_asn = neighbor["asn"]
        else:
            # logical link mode: neighbor is node name
            neighbor_ip = node_info[neighbor][ip_key]
            neighbor_asn = node_info[neighbor]["asn"]

        lines.extend([f"    neighbor {neighbor_ip} remote-as {neighbor_asn}"])

    # configure address family
    lines.extend([f"    !", f"    address-family {af_name} unicast"])

    # configure neighbor attributes in address family
    for neighbor in neighbors:
        neighbor_ip = (
            neighbor[ip_key]
            if isinstance(neighbor, dict)
            else node_info[neighbor][ip_key]
        )

        lines.extend(
            [
                f"        neighbor {neighbor_ip} activate",
                f"        neighbor {neighbor_ip} next-hop-self",
            ]
        )

    lines.extend([f"    exit-address-family", f"    !"])

    return "\n".join(lines)


def create_as_node_dirs(node_name, neighbors, node_info, output_folder, ipv6=True):
    node_folder = os.path.join(output_folder, node_name)
    os.makedirs(node_folder, exist_ok=True)

    # 1. create frr_conf directory
    frr_dir = os.path.join(node_folder, "frr_conf")
    os.makedirs(frr_dir, exist_ok=True)

    # create daemons file
    with open(os.path.join(frr_dir, "daemons"), "w", newline="\n") as f:
        f.write(template.DEFAULT_FRR_DAEMONS_CONF)
    # create zebra.conf file
    with open(os.path.join(frr_dir, "zebra.conf"), "w", newline="\n") as f:
        f.write(template.DEFAULT_FRR_ZEBRA_CONF)
    # create bgpd.conf file
    bgpd_conf_content = prepare_bgpd_conf(node_name, neighbors, node_info, ipv6=ipv6)

    with open(os.path.join(frr_dir, "bgpd.conf"), "w", newline="\n") as f:
        f.write(bgpd_conf_content)

    # 2. create history directory
    history_dir = os.path.join(node_folder, "history")
    os.makedirs(history_dir, exist_ok=True)

    # 3. create ready directory
    ready_dir = os.path.join(node_folder, "ready")
    os.makedirs(ready_dir, exist_ok=True)

    # 4. create scripts directory
    scripts_dir = os.path.join(node_folder, "scripts")
    os.makedirs(scripts_dir, exist_ok=True)


def create_host_node_dirs(node_name, output_folder):
    node_folder = os.path.join(output_folder, node_name)
    os.makedirs(node_folder, exist_ok=True)

    # 1. create history directory
    volumes_dir = os.path.join(node_folder, "history")
    os.makedirs(volumes_dir, exist_ok=True)

    # 2. create ready directory
    ready_dir = os.path.join(node_folder, "ready")
    os.makedirs(ready_dir, exist_ok=True)

    # 3. create scripts directory
    scripts_dir = os.path.join(node_folder, "scripts")
    os.makedirs(scripts_dir, exist_ok=True)


def generate(
    output_folder: str,
    node_count: int,
    avg_degree: int,
    subnet: str,
    network_access: bool,
    ipv6_subnet: str,
    mac_prefix: str,
    bgp_ipv6: bool,
):
    """generate topology config files"""
    os.makedirs(output_folder, exist_ok=True)

    as_node_count = node_count // 2
    node, links = generate_nodes_links(as_node_count, avg_degree)

    topology = {
        "nodes": node,
        "links": links,
        "templates": {
            "frr": template.DEFAULT_FRR_TEMPLATE,
            "openeuler-hosts": template.DEFAULT_HOST_TEMPLATE,
        },
    }

    # deduplicate links
    unique = set()
    for link in links:
        unique.add(tuple(sorted(link)))
    topology["links"] = list(unique)

    # Initialize node structure
    for node_name, node_data in topology["nodes"].items():
        node_data["template"] = "frr"
        node_data["internet_access"] = network_access
        node_data["vNICs"] = {}

    # Assign ASN to each node
    node_asn = {name: i + 1 for i, name in enumerate(topology["nodes"])}

    # Store neighbors info for BGP config
    node_neighbors = {node: [] for node in topology["nodes"]}
    modified_links = []

    # Physical links mode - assign independent subnets to each link
    if len(topology["links"]) > 65535:
        raise ValueError("Link count cannot exceed 65535 in physical links mode")

    for link_index, link in enumerate(topology["links"]):
        node_a, node_b = link

        # Generate subnet for the link
        link_subnet = generate_link_subnet(subnet, link_index)
        link_ipv6_subnet = generate_link_ipv6_subnet(ipv6_subnet, link_index)

        # Generate IP and MAC for the link endpoints
        ip_gen = ip_network(link_subnet).hosts()
        ipv6_gen = ip_network(link_ipv6_subnet).hosts()

        ip_a, ip_b = str(next(ip_gen)), str(next(ip_gen))
        ipv6_a, ipv6_b = str(next(ipv6_gen)), str(next(ipv6_gen))
        # Use 2 bytes to store link_index and node_asn
        mac_a = f"{mac_prefix}:{(link_index >> 8):02x}:{link_index & 0xFF:02x}:{node_asn[node_a] & 0xFF:02x}"
        mac_b = f"{mac_prefix}:{(link_index >> 8):02x}:{link_index & 0xFF:02x}:{node_asn[node_b] & 0xFF:02x}"

        # Assign NICs to each node
        nic_a = f"eth{len(topology['nodes'][node_a]['vNICs'])}"
        nic_b = f"eth{len(topology['nodes'][node_b]['vNICs'])}"

        # Configure NICs for each node
        topology["nodes"][node_a]["vNICs"][nic_a] = {
            "ip": [
                f"{ip_a}/{link_subnet.split('/')[1]}",
                f"{ipv6_a}/{link_ipv6_subnet.split('/')[1]}",
            ],
            "mac": mac_a,
        }
        topology["nodes"][node_b]["vNICs"][nic_b] = {
            "ip": [
                f"{ip_b}/{link_subnet.split('/')[1]}",
                f"{ipv6_b}/{link_ipv6_subnet.split('/')[1]}",
            ],
            "mac": mac_b,
        }

        # Add neighbor info for both nodes
        node_neighbors[node_a].append(
            {"node": node_b, "ip": ip_b, "ipv6": ipv6_b, "asn": node_asn[node_b]}
        )
        node_neighbors[node_b].append(
            {"node": node_a, "ip": ip_a, "ipv6": ipv6_a, "asn": node_asn[node_a]}
        )

        modified_links.append([f"{node_a}:{nic_a}", f"{node_b}:{nic_b}"])

    topology["links"] = modified_links

    # generate config files for each node
    for node_name in topology["nodes"]:
        neighbors = node_neighbors[node_name]
        asn = node_asn[node_name]
        create_as_node_dirs(node_name, neighbors, asn, output_folder, bgp_ipv6)

    # generate host config file and update corresponding as node's config for each as node
    """
    1. Before generating the Host node, the AS node currently has n network cards, and each network card is interconnected with its neighbor AS, all of which are internal networks
    2. When generating the Host node, a subnet connection is created between this AS and its Host node. AS adds a new network card N1 (with the highest number), and the Host adds a network card N2 (eth0)
    3. N1 and N2 are interconnected, and belong to the same network segment S. If AS announces S in its bgp configuration, then this network segment can be accessed in the Internet
    4. The Host can connect to the Internet through the router by using N1 as the default gateway
    5. Because of the operations in 3 and 4, both N1 and N2 can access the Internet and can also be pinged
    6. Therefore, when setting the IP addresses that can be pinged for each node, the AS node can find the IP address corresponding to the highest numbered network card, and the Host node can find eth0
    """
    host_links = []
    for node_name in topology["nodes"].copy():
        link_index = len(topology["links"]) + len(host_links)

        # generate host subnet
        host_subnet = generate_link_subnet(subnet, link_index)
        host_ipv6_subnet = generate_link_ipv6_subnet(ipv6_subnet, link_index)

        # generate host IP and MAC address
        ip_gen = ip_network(host_subnet).hosts()
        ipv6_gen = ip_network(host_ipv6_subnet).hosts()

        # generate host ip and corresponding AS's binding ip
        # as_ip is the binding ip for AS node, host_ip is the binding ip for host node, they are in the same subnet
        as_ip, host_ip = str(next(ip_gen)), str(next(ip_gen))
        as_ipv6, host_ipv6 = str(next(ipv6_gen)), str(next(ipv6_gen))

        # generate host MAC address, consistent with physical links mode
        host_mac = f"{mac_prefix}:{(link_index >> 8):02x}:{link_index & 0xFF:02x}:01"
        node_mac = f"{mac_prefix}:{(link_index >> 8):02x}:{link_index & 0xFF:02x}:02"

        # === configure host node ===
        host_name = f"{node_name}_host"
        topology["nodes"][host_name] = {
            "template": "openeuler-hosts",
            "internet_access": network_access,
            "start_commands": [
                f"ip route add default via {as_ip} dev eth0",
                f"ip route add {subnet} via {as_ip} dev eth0",
                f"ip -6 route add default via {as_ipv6} dev eth0",
                f"ip -6 route add {ipv6_subnet} via {as_ipv6} dev eth0",
                "chmod -R 777 /usr/routebox/scripts && (bash /usr/routebox/scripts/start_log.sh &)",
            ],
            "stop_commands": [
                f"ip route del default via {as_ip} dev eth0",
                f"ip route del {subnet} via {as_ip} dev eth0",
                f"ip -6 route del default via {as_ipv6} dev eth0",
                f"ip -6 route del {ipv6_subnet} via {as_ipv6} dev eth0",
                "bash /usr/routebox/scripts/stop_log.sh",
            ],
            "vNICs": {
                "eth0": {
                    "ip": [
                        f"{host_ip}/{host_subnet.split('/')[1]}",
                        f"{host_ipv6}/{host_ipv6_subnet.split('/')[1]}",
                    ],
                    "mac": host_mac,
                }
            },
        }
        # create volumes directory for host
        create_host_node_dirs(host_name, output_folder)

        # === update as node's config ===
        # append new NIC to as node to connect with its host
        nic_name = f"eth{len(topology['nodes'][node_name]['vNICs'])}"
        topology["nodes"][node_name]["vNICs"][nic_name] = {
            "ip": [
                f"{as_ip}/{host_subnet.split('/')[1]}",
                f"{as_ipv6}/{host_ipv6_subnet.split('/')[1]}",
            ],
            "mac": node_mac,
        }

        # append network statement to corresponding AS node's bgpd.conf
        bgpd_conf_path = os.path.join(output_folder, node_name, "frr_conf", "bgpd.conf")
        with open(bgpd_conf_path, "r") as f:
            lines = f.readlines()

        # find address-family section and add network statement
        ipv4_found = False
        ipv6_found = False

        for i, line in enumerate(lines):
            if "address-family ipv4 unicast" in line:
                ipv4_found = True
                network_subnet = str(ip_network(host_subnet).network_address)
                prefix_len = host_subnet.split("/")[1]
                # insert network statement
                lines.insert(i + 1, f"        network {network_subnet}/{prefix_len}\n")
            elif "address-family ipv6 unicast" in line:
                ipv6_found = True
                # find network statement position
                network_subnet = str(ip_network(host_ipv6_subnet).network_address)
                prefix_len = host_ipv6_subnet.split("/")[1]
                # insert network statement
                lines.insert(i + 1, f"        network {network_subnet}/{prefix_len}\n")

        # if no corresponding address-family is found, add it
        if not ipv4_found:
            lines.extend(
                [
                    "\n",
                    "    !\n",
                    "    address-family ipv4 unicast\n",
                    f"        network {str(ip_network(host_subnet).network_address)}/{host_subnet.split('/')[1]}\n",
                    "    exit-address-family\n",
                    "    !\n",
                ]
            )
        if not ipv6_found:
            lines.extend(
                [
                    "\n",
                    "\n",
                    "    !\n",
                    "    address-family ipv6 unicast\n",
                    f"        network {str(ip_network(host_ipv6_subnet).network_address)}/{host_ipv6_subnet.split('/')[1]}\n",
                    "    exit-address-family\n",
                    "    !\n",
                ]
            )

        # write back to file
        with open(bgpd_conf_path, "w") as f:
            f.writelines(lines)

        # add connection
        host_links.append([f"{node_name}:{nic_name}", f"{host_name}:eth0"])

    # Collect all nodes' IP addresses for get_ping_log.sh
    all_ips = []
    host_ips = []
    for node_name, node_data in topology["nodes"].items():
        # For host nodes, use the only IP
        if node_data["template"] == "openeuler-hosts":
            ip = node_data["vNICs"]["eth0"]["ip"][0].split("/")[0]
            all_ips.append(ip)
            host_ips.append(ip)
        else:
            # For router nodes, get the IP from the eth with the highest number
            max_nic = None
            for nic in node_data["vNICs"]:
                if nic.startswith("eth"):
                    if max_nic is None:
                        max_nic = nic
                    else:
                        # Compare numerical part of NIC name
                        current_num = int(nic[3:])
                        if current_num > int(max_nic[3:]):
                            max_nic = nic
            if max_nic:
                all_ips.append(node_data["vNICs"][max_nic]["ip"][0].split("/")[0])

    # Generate servers list for traffic_service.py
    server_count = max(1, int(len(host_ips) * 0.1))
    # Randomly select servers
    server_ips = random.sample(host_ips, server_count)

    # Adaptive traffic parameters based on server count
    REFERENCE_SERVER_COUNT = 5
    REFERENCE_SMALL_SIZE = 6144
    REFERENCE_MEDIUM_SIZE = 10240
    REFERENCE_LARGE_SIZE = 51200
    REFERENCE_BASE_INTERVAL = 1.0
    MAX_SCALING_FACTOR = 10.0
    MIN_BASE_INTERVAL = 0.3

    if server_count < REFERENCE_SERVER_COUNT:
        scaling_factor = min(MAX_SCALING_FACTOR, REFERENCE_SERVER_COUNT / server_count)
        adaptive_small_size = int(REFERENCE_SMALL_SIZE * scaling_factor)
        adaptive_medium_size = int(REFERENCE_MEDIUM_SIZE * scaling_factor)
        adaptive_large_size = int(REFERENCE_LARGE_SIZE * scaling_factor)
        adaptive_base_interval = max(
            MIN_BASE_INTERVAL, REFERENCE_BASE_INTERVAL / scaling_factor
        )
    else:
        adaptive_small_size = REFERENCE_SMALL_SIZE
        adaptive_medium_size = REFERENCE_MEDIUM_SIZE
        adaptive_large_size = REFERENCE_LARGE_SIZE
        adaptive_base_interval = REFERENCE_BASE_INTERVAL

    # prepare to update templates
    # 1. read get_ping_log.sh template
    ping_log_template_content = ""
    with open("./templates/host/get_ping_log.sh", "r") as f:
        ping_log_template_content = f.read()
    # 2. update get_ping_log.sh template
    ip_list_str = '"' + '" "'.join(all_ips) + '"'
    ping_log_template_content = ping_log_template_content.replace(
        "# all_ips",
        f"all_ips=({ip_list_str})",
    )

    # 3. read traffic_service.py template
    traffic_service_template_content = ""
    with open("./templates/host/traffic_service.py", "r") as f:
        traffic_service_template_content = f.read()
    # 4. update traffic_service.py template
    server_ip_list = ",".join([f'"{ip}"' for ip in server_ips])
    traffic_service_template_content = traffic_service_template_content.replace(
        "# SERVERS, SMALL_SIZE, MEDIUM_SIZE, LARGE_SIZE, BASE_INTERVAL",
        f"SERVERS = [{server_ip_list}]\n"
        f"SMALL_SIZE = {adaptive_small_size}\n"
        f"MEDIUM_SIZE = {adaptive_medium_size}\n"
        f"LARGE_SIZE = {adaptive_large_size}\n"
        f"BASE_INTERVAL = {adaptive_base_interval}",
    )

    # Update scripts for all nodes
    for node_name, node_data in topology["nodes"].items():
        scripts_dir = os.path.join(output_folder, node_name, "scripts")

        if node_data["template"] == "openeuler-hosts":
            # Host node scripts
            template_dir = "./templates/host"
            # Copy all scripts except traffic_service.py
            for script_file in [
                "get_system_network_log.sh",
                "start_log.sh",
                "stop_log.sh",
            ]:
                src_path = os.path.join(template_dir, script_file)
                dst_path = os.path.join(scripts_dir, script_file)
                shutil.copy2(src_path, dst_path)

            # copy updated get_ping_log.sh
            ping_log_path = os.path.join(scripts_dir, "get_ping_log.sh")
            with open(ping_log_path, "w", newline="\n") as f:
                f.write(ping_log_template_content)

            # copy updated traffic_service.py
            traffic_service_path = os.path.join(scripts_dir, "traffic_service.py")
            with open(traffic_service_path, "w", newline="\n") as f:
                f.write(traffic_service_template_content)

        else:
            template_dir = "./templates/router"

            for script_file in [
                "get_bgp_log.sh",
                "get_system_network_log.sh",
                "start_log.sh",
                "stop_log.sh",
            ]:
                src_path = os.path.join(template_dir, script_file)
                dst_path = os.path.join(scripts_dir, script_file)
                shutil.copy2(src_path, dst_path)

            # copy updated get_ping_log.sh
            ping_log_path = os.path.join(scripts_dir, "get_ping_log.sh")
            with open(ping_log_path, "w", newline="\n") as f:
                f.write(ping_log_template_content)

    # update YAML structure
    yaml_dict = {
        "templates": topology["templates"],
        "nodes": topology["nodes"],
    }

    all_links = topology["links"] + host_links
    yaml_dict["links"] = {"veth_pairs": all_links}

    yaml_str = yaml.dump(
        yaml_dict,
        default_flow_style=False,
        sort_keys=False,
        Dumper=Dumper,
        allow_unicode=True,
        width=1000, # avoid auto wrap
    )
    with open(os.path.join(output_folder, "config.yaml"), "w", newline="\n") as f:
        f.write(yaml_str)


def generate_topology(
    node_count: int,
    avg_degree: float,
    subnet: str,
    ipv6_subnet: str,
    network_access: bool,
    network_name: str,
    output_folder: str,
) -> dict:

    if not network_name:
        network_name = f"network_{node_count}_{avg_degree}"

    if node_count % 2 != 0:
        raise ValueError("node_count must be even")

    generate(
        output_folder=f"{output_folder}/{network_name}",
        node_count=node_count,
        avg_degree=avg_degree,
        subnet=subnet,
        network_access=network_access,
        ipv6_subnet=ipv6_subnet,
        bgp_ipv6=True,
        mac_prefix="36:50:fc",
    )

    print(f"Topology {network_name} generated successfully")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Generate network topology")
    parser.add_argument(
        "-ac",
        "--as-count",
        type=int,
        required=True,
        help="Total number of ASes",
    )
    parser.add_argument(
        "-ad",
        "--avg-degree",
        type=float,
        required=True,
        help="Average degree of the topology",
    )
    parser.add_argument(
        "-s",
        "--subnet",
        type=str,
        default="100.0.0.0/8",
        help="Subnet for IPv4 addresses (default: 100.0.0.0/8)",
    )
    parser.add_argument(
        "-s6",
        "--ipv6-subnet",
        type=str,
        default="fd00:100::/64",
        help="Subnet for IPv6 addresses (default: fd00:100::/64)",
    )
    parser.add_argument(
        "-na",
        "--network-access",
        type=bool,
        default=False,
        help="Enable all nodes to access the Internet (default: False)",
    )
    parser.add_argument(
        "-nn",
        "--network-name",
        type=str,
        default="",
        help="Name of the network (default: network_<node_count>_<avg_degree>)",
    )
    parser.add_argument(
        "-of",
        "--output-folder",
        type=str,
        default="./output",
        help="Output folder path (default: ./output)",
    )

    args = parser.parse_args()

    generate_topology(
        node_count=args.as_count * 2,
        avg_degree=args.avg_degree,
        subnet=args.subnet,
        ipv6_subnet=args.ipv6_subnet,
        network_access=args.network_access,
        network_name=args.network_name,
        output_folder=args.output_folder,
    )
