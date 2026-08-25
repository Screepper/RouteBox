package com.routebox.entity.index.auxiliary;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Description BGP Network Declaration Info
 * @Date 2025/3/30
**/
@Data
@AllArgsConstructor
@NoArgsConstructor
public class BgpNetworkDeclaration{
    private String ipPrefix; // BGP Network Declaration IP Prefix
    private String routeMapName; // BGP Network Declaration Route Map Name (Optional)
    private String mask; // BGP Network Declaration Mask (Optional)

}