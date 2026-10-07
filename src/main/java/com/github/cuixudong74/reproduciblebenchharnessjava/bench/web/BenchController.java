package com.github.cuixudong74.reproduciblebenchharnessjava.bench.web;

import com.github.cuixudong74.reproduciblebenchharnessjava.bench.core.BenchConfig;
import com.github.cuixudong74.reproduciblebenchharnessjava.bench.core.RunReport;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 基准测试 REST 入口。
 */
@RestController
@RequestMapping("/bench")
public class BenchController {

    private final BenchService benchService;

    public BenchController(BenchService benchService) {
        this.benchService = benchService;
    }

    /** 运行一次基准并自动比对基线（不写基线）。 */
    @PostMapping("/run")
    public BenchService.RunAndComparison run(@RequestBody BenchConfig config) {
        return benchService.run(config);
    }

    /** 显式更新基线：运行一次并把结果固化为基线，旧基线保留可回退。 */
    @PostMapping("/baseline/update")
    public RunReport updateBaseline(@RequestBody BenchConfig config) {
        return benchService.runAndUpdateBaseline(config);
    }

    /** 回退到上一份基线。 */
    @PostMapping("/baseline/{workloadName}/rollback")
    public ResponseEntity<Void> rollback(@PathVariable String workloadName) {
        return benchService.rollbackBaseline(workloadName)
                ? ResponseEntity.ok().build()
                : ResponseEntity.notFound().build();
    }

    /** 查看当前基线。 */
    @GetMapping("/baseline/{workloadName}")
    public ResponseEntity<RunReport> getBaseline(@PathVariable String workloadName) {
        return benchService.loadBaseline(workloadName)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
