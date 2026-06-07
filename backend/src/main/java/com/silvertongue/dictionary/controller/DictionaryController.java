package com.silvertongue.dictionary.controller;

import com.silvertongue.common.ApiResult;
import com.silvertongue.dictionary.grpc.DictionaryGrpcClient;
import com.silvertongue.grpc.dictionary.DictEntry;
import com.silvertongue.grpc.dictionary.LookupResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 词典查询 REST 控制器
 *
 * 前端划词查询入口：GET /api/dict/lookup?word=hello
 * 返回剑桥词典释义（音标、词性、定义、例句、发音URL）
 */
@Slf4j
@RestController
@RequestMapping("/api/dict")
@RequiredArgsConstructor
public class DictionaryController {

    private final DictionaryGrpcClient dictionaryGrpcClient;

    @GetMapping("/lookup")
    public ApiResult<Map<String, Object>> lookup(@RequestParam("word") String word) {
        if (word == null || word.isBlank()) {
            return ApiResult.error(400, "word is required");
        }

        LookupResponse response = dictionaryGrpcClient.lookup(word.trim());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("found", response.getFound());
        result.put("word", response.getWord());
        result.put("entries", response.getEntriesList().stream()
                .map(DictionaryController::entryToMap)
                .collect(Collectors.toList()));

        return ApiResult.success(result);
    }

    private static Map<String, Object> entryToMap(DictEntry entry) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("pos", entry.getPos());
        map.put("definition", entry.getDefinition());
        map.put("translation", entry.getTranslation());
        map.put("examples", entry.getExamplesList());
        map.put("phoneticsUk", entry.getPhoneticsUk());
        map.put("phoneticsUs", entry.getPhoneticsUs());
        map.put("audioUkUrl", entry.getAudioUkUrl());
        map.put("audioUsUrl", entry.getAudioUsUrl());
        return map;
    }
}
