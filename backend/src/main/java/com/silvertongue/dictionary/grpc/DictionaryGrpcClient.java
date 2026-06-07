package com.silvertongue.dictionary.grpc;

import com.silvertongue.grpc.dictionary.DictionaryServiceGrpc;
import com.silvertongue.grpc.dictionary.LookupRequest;
import com.silvertongue.grpc.dictionary.LookupResponse;
import io.grpc.ManagedChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * gRPC 客户端 — 调用 Python Agent 的 DictionaryService (剑桥词典查词)
 *
 * 参考 Enjoy App camdict.ts 的 SQLite 剑桥词典查词设计，
 * 由 Python Agent 读取 cam_dict.refined.sqlite 提供服务。
 */
@Slf4j
@Service
public class DictionaryGrpcClient {

    private final DictionaryServiceGrpc.DictionaryServiceBlockingStub stub;

    public DictionaryGrpcClient(ManagedChannel channel) {
        this.stub = DictionaryServiceGrpc.newBlockingStub(channel);
    }

    /**
     * 查询单词的词典释义（剑桥词典）
     *
     * @param word 待查询的单词
     * @return gRPC LookupResponse，包含 found、word 和 entries 列表
     */
    public LookupResponse lookup(String word) {
        LookupRequest request = LookupRequest.newBuilder()
                .setWord(word)
                .build();

        log.info("Calling gRPC LookupWord: word={}", word);

        try {
            LookupResponse response = stub.lookupWord(request);
            if (response.getFound()) {
                log.info("Dictionary lookup found for '{}': {} entries", word, response.getEntriesCount());
            } else {
                log.info("Dictionary lookup not found for '{}'", word);
            }
            return response;
        } catch (Exception e) {
            log.error("gRPC LookupWord call failed for '{}': {}", word, e.getMessage());
            return LookupResponse.newBuilder().setFound(false).setWord(word).build();
        }
    }
}
