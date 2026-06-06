package com.silvertongue.harvester.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.silvertongue.harvester.dto.ClipCreateRequest;
import com.silvertongue.harvester.dto.ClipVO;
import com.silvertongue.harvester.entity.Clip;
import com.silvertongue.harvester.entity.Material;
import com.silvertongue.harvester.mapper.ClipMapper;
import com.silvertongue.harvester.mapper.MaterialMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClipService {

    private final ClipMapper clipMapper;
    private final MaterialMapper materialMapper;

    /**
     * 从素材创建切片
     */
    @Transactional
    public ClipVO create(ClipCreateRequest request) {
        Material material = materialMapper.selectById(request.getMaterialId());
        if (material == null) {
            throw new IllegalArgumentException("material not found");
        }

        // 校验时间范围
        if (request.getStartTime().compareTo(request.getEndTime()) >= 0) {
            throw new IllegalArgumentException("startTime must be before endTime");
        }
        if (request.getStartTime().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("startTime must be non-negative");
        }

        Clip clip = new Clip();
        clip.setMaterialId(request.getMaterialId());
        clip.setStartTime(request.getStartTime());
        clip.setEndTime(request.getEndTime());
        clip.setContent(request.getContent());
        clip.setTranslation(request.getTranslation());
        clip.setCreateTime(LocalDateTime.now());
        clipMapper.insert(clip);

        log.info("Clip created: id={}, materialId={}, time=[{}-{}]", clip.getId(), request.getMaterialId(), request.getStartTime(), request.getEndTime());

        return toVO(clip);
    }

    /**
     * 查询某素材下的所有切片
     */
    public List<ClipVO> listByMaterial(Long materialId) {
        List<Clip> clips = clipMapper.selectList(new LambdaQueryWrapper<Clip>()
                .eq(Clip::getMaterialId, materialId)
                .orderByAsc(Clip::getStartTime));
        return clips.stream().map(this::toVO).collect(Collectors.toList());
    }

    /**
     * 分页查询切片列表
     */
    public List<ClipVO> listAll(int page, int size) {
        int offset = (page - 1) * size;
        List<Clip> clips = clipMapper.selectList(new LambdaQueryWrapper<Clip>()
                .orderByDesc(Clip::getCreateTime)
                .last("LIMIT " + offset + "," + size));
        return clips.stream().map(this::toVO).collect(Collectors.toList());
    }

    /**
     * 浏览器插件采集：创建 Material + Clip，返回 clipId
     */
    @Transactional
    public ClipVO harvest(String url, String platform, Double startTime, Double endTime) {
        // 1. 创建/查找 Material
        Material material = new Material();
        material.setMd5(org.apache.commons.codec.digest.DigestUtils.md5Hex(url));
        material.setTitle("Harvested from " + platform);
        material.setType("video");
        material.setSourceUrl(url);
        material.setStatus(1); // 下载中
        material.setCreateTime(LocalDateTime.now());
        material.setUpdateTime(LocalDateTime.now());

        // 检查是否已存在（秒传去重）
        Material existing = materialMapper.selectOne(new LambdaQueryWrapper<Material>()
                .eq(Material::getMd5, material.getMd5()));
        if (existing != null) {
            material = existing;
        } else {
            materialMapper.insert(material);
        }

        // 2. 创建 Clip
        Clip clip = new Clip();
        clip.setMaterialId(material.getId());
        clip.setStartTime(BigDecimal.valueOf(startTime));
        clip.setEndTime(BigDecimal.valueOf(endTime));
        clip.setStatus(0); // 待处理
        clip.setCreateTime(LocalDateTime.now());
        clipMapper.insert(clip);

        log.info("Harvest clip created: clipId={}, materialId={}, url={}, time=[{}-{}]",
                clip.getId(), material.getId(), url, startTime, endTime);

        // 3. 异步触发下载 (TODO: 调用 Python Agent gRPC)
        triggerAsyncDownload(clip.getId(), url, startTime, endTime);

        return toVO(clip);
    }

    /**
     * 查询切片的处理状态
     */
    public ClipVO getStatus(Long clipId) {
        Clip clip = clipMapper.selectById(clipId);
        if (clip == null) {
            throw new IllegalArgumentException("clip not found");
        }
        return toVO(clip);
    }

    /**
     * 异步触发下载（当前使用 yt-dlp 命令行，后续可切 gRPC）
     */
    private void triggerAsyncDownload(Long clipId, String url, Double startTime, Double endTime) {
        new Thread(() -> {
            try {
                // 更新状态为下载中
                Clip clip = clipMapper.selectById(clipId);
                clip.setStatus(1);
                clipMapper.updateById(clip);

                // 调用 Python Agent 的 harvester 服务
                // TODO: 替换为 gRPC 调用
                String pythonCmd = String.format(
                        "python ai-agent/services/harvester.py --clip-id %d --url \"%s\" --start %.2f --end %.2f",
                        clipId, url, startTime, endTime
                );
                Process process = Runtime.getRuntime().exec(pythonCmd);
                int exitCode = process.waitFor();

                if (exitCode == 0) {
                    clip.setStatus(3); // 已完成
                } else {
                    clip.setStatus(4); // 失败
                }
                clipMapper.updateById(clip);
            } catch (Exception e) {
                log.error("Async download failed: clipId={}", clipId, e);
                Clip clip = clipMapper.selectById(clipId);
                clip.setStatus(4);
                clipMapper.updateById(clip);
            }
        }, "harvest-clip-" + clipId).start();
    }

    /**
     * Python Agent 回调：更新切片状态和存储路径
     */
    public void updateStatus(Long clipId, int status, String storagePath) {
        Clip clip = clipMapper.selectById(clipId);
        if (clip != null) {
            clip.setStatus(status);
            clipMapper.updateById(clip);

            // 同时更新 Material 的 storagePath
            if (storagePath != null && !storagePath.isEmpty()) {
                Material material = materialMapper.selectById(clip.getMaterialId());
                if (material != null && material.getStoragePath() == null) {
                    material.setStoragePath(storagePath);
                    material.setStatus(status == 3 ? 2 : 4); // 已完成 → 转录中, 失败 → 失败
                    material.setUpdateTime(LocalDateTime.now());
                    materialMapper.updateById(material);
                }
            }
            log.info("Clip status updated: clipId={}, status={}", clipId, status);
        }
    }

    private ClipVO toVO(Clip c) {
        return ClipVO.builder()
                .id(c.getId())
                .materialId(c.getMaterialId())
                .startTime(c.getStartTime())
                .endTime(c.getEndTime())
                .content(c.getContent())
                .translation(c.getTranslation())
                .vectorId(c.getVectorId())
                .status(c.getStatus())
                .createTime(c.getCreateTime())
                .build();
    }
}
