package com.routebox.entity.topology;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @Description SERNES Log Download
 * @Date 2025/5/28
**/
@Data
@AllArgsConstructor
@NoArgsConstructor
public class LogDownload {
    private List<Integer> node_ids; // NodeIds that need to download batch-logs
    private String volumes; // The file bytes
}
