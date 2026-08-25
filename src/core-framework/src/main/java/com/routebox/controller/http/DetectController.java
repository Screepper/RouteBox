package com.routebox.controller.http;

import com.routebox.common.response.R;
import com.routebox.entity.index.DetectResultLog;
import com.routebox.entity.page.PageQuery;
import com.routebox.entity.page.PageVO;
import com.routebox.service.EsService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @Description
 * @Date 2025/12/17
**/

@RestController
@RequestMapping("/detect")
public class DetectController {
    private final EsService esService;

    public DetectController(EsService esService) {
        this.esService = esService;
    }

    /**
     * @Description Load attack records
     * @Date 2025/12/14
     **/
    @PostMapping("/loadDetectRecords")
    public R<PageVO<DetectResultLog>> loadDetectRecords(@RequestBody PageQuery pageQuery) {
        int limit = pageQuery.getPageSize();
        int start = limit * (pageQuery.getCurrentPage() - 1);

        PageVO<DetectResultLog> pageVO = esService.searchPage2PageVO(limit, start, DetectResultLog.class);

        return R.ok(pageVO);
    }

    /**
     * @Description Load latest detect records
     * @Date 2026/3/10
     **/
    @GetMapping("/loadLatestRecord")
    public R<DetectResultLog> loadLatestRecord() {
        List<DetectResultLog> detectResultLogs = esService.search(DetectResultLog.class, false, 1);
        return R.ok((detectResultLogs == null || detectResultLogs.isEmpty()) ? null : detectResultLogs.getFirst());
    }

    @PostMapping("/updateLatestDetectRecord")
    public R<String> updateLatestDetectRecord(@RequestBody DetectResultLog detectResultLog) {
        esService.update(detectResultLog);
        return R.ok("success");
    }
}
