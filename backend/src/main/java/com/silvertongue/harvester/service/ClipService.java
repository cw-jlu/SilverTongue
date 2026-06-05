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

    private ClipVO toVO(Clip c) {
        return ClipVO.builder()
                .id(c.getId())
                .materialId(c.getMaterialId())
                .startTime(c.getStartTime())
                .endTime(c.getEndTime())
                .content(c.getContent())
                .translation(c.getTranslation())
                .vectorId(c.getVectorId())
                .createTime(c.getCreateTime())
                .build();
    }
}
