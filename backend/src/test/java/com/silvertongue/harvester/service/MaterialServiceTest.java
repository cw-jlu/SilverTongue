package com.silvertongue.harvester.service;

import com.silvertongue.harvester.dto.MaterialVO;
import com.silvertongue.harvester.entity.Material;
import com.silvertongue.harvester.mapper.MaterialMapper;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MaterialServiceTest {

    @Mock
    private MinioClient minioClient;

    @Mock
    private MaterialMapper materialMapper;

    @InjectMocks
    private MaterialService materialService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(materialService, "bucketName", "test-materials");
    }

    @Test
    void uploadShouldReturnExistingRecordWhenMd5AlreadyExists() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "lesson.pdf", "application/pdf", "same-content".getBytes()
        );
        Material existing = new Material();
        existing.setId(1L);
        existing.setMd5("existing-md5");
        existing.setTitle("lesson");

        when(materialMapper.selectOne(any())).thenReturn(existing);

        MaterialVO result = materialService.upload(file);

        assertEquals(1L, result.getId());
        assertEquals("lesson", result.getTitle());
        verify(minioClient, never()).putObject(any(PutObjectArgs.class));
        verify(materialMapper, never()).insert(any(Material.class));
    }

    @Test
    void uploadShouldStoreNewMaterialInMinioAndDatabase() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "lesson.mp3", "audio/mpeg", "fresh-content".getBytes()
        );

        when(materialMapper.selectOne(any())).thenReturn(null);
        doAnswer(invocation -> {
            Material material = invocation.getArgument(0);
            material.setId(22L);
            return 1;
        }).when(materialMapper).insert(any(Material.class));

        MaterialVO result = materialService.upload(file);

        ArgumentCaptor<PutObjectArgs> putCaptor = ArgumentCaptor.forClass(PutObjectArgs.class);
        ArgumentCaptor<Material> materialCaptor = ArgumentCaptor.forClass(Material.class);
        verify(minioClient).putObject(putCaptor.capture());
        verify(materialMapper).insert(materialCaptor.capture());

        Material inserted = materialCaptor.getValue();
        assertEquals(22L, result.getId());
        assertEquals("lesson", inserted.getTitle());
        assertEquals("audio", inserted.getType());
        assertEquals("audio", result.getType());
        assertTrue(inserted.getStoragePath().startsWith("materials/"));
        assertEquals("test-materials", putCaptor.getValue().bucket());
    }
}
