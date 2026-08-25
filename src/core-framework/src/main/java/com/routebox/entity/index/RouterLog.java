package com.routebox.entity.index;

import com.routebox.entity.index.auxiliary.VtyshInfo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @Description Elasticsearch Entity: Router Commands Log
 * @Date 2025/5/27
**/
@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RouterLog extends IndexBase {
    private List<VtyshInfo> histories; // Command Histories
}
