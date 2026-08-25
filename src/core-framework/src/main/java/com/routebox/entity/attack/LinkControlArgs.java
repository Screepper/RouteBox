package com.routebox.entity.attack;

import com.routebox.common.constants.Constants;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;


/**
 * @Description SERNES Link Control Arguments
 * @Date 2025/6/26
**/
@Data
@AllArgsConstructor
@NoArgsConstructor
public class LinkControlArgs implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private Integer bw; // Bandwidth in Mbps
    private Integer delay; // Delay in milliseconds
    private Float loss; // Packet loss rate

    public static LinkControlArgs defaultArgs() {
        return new LinkControlArgs(Constants.DEFAULT_LINK_BW, Constants.DEFAULT_LINK_DELAY, Constants.DEFAULT_LINK_LOSS);
    }
}