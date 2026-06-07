package com.silvertongue.harvester.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.silvertongue.harvester.entity.Clip;
import com.silvertongue.harvester.entity.Material;
import com.silvertongue.harvester.mapper.ClipMapper;
import com.silvertongue.harvester.mapper.MaterialMapper;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClipServiceTest {

    @Mock
    private ClipMapper clipMapper;

    @Mock
    private MaterialMapper materialMapper;

    @Mock
    private MinioClient minioClient;

    @InjectMocks
    private ClipService clipService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(clipService, "aiAgentBaseUrl", "http://localhost:8089");
        ReflectionTestUtils.setField(clipService, "materialsBucketName", "test-materials");
        ReflectionTestUtils.setField(clipService, "objectMapper", new ObjectMapper());
    }

    @Test
    void getPlaybackUrlShouldReturnPresignedMinioUrlWhenStoragePathExists() throws Exception {
        Clip clip = new Clip();
        clip.setId(11L);
        clip.setMaterialId(22L);
        clip.setStartTime(BigDecimal.ZERO);
        clip.setEndTime(BigDecimal.ONE);

        Material material = new Material();
        material.setId(22L);
        material.setStoragePath("materials/aa/test.mp3");

        when(clipMapper.selectById(11L)).thenReturn(clip);
        when(materialMapper.selectById(22L)).thenReturn(material);
        when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenReturn("http://minio/presigned");

        String result = clipService.getPlaybackUrl(11L);

        assertEquals("http://minio/presigned", result);
        verify(minioClient).getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class));
    }

    @Test
    void getPlaybackUrlShouldFallBackToSourceUrlWhenObjectStorageMissing() {
        Clip clip = new Clip();
        clip.setId(12L);
        clip.setMaterialId(23L);

        Material material = new Material();
        material.setId(23L);
        material.setSourceUrl("https://example.com/audio.mp3");

        when(clipMapper.selectById(12L)).thenReturn(clip);
        when(materialMapper.selectById(23L)).thenReturn(material);

        String result = clipService.getPlaybackUrl(12L);

        assertEquals("https://example.com/audio.mp3", result);
    }
}
