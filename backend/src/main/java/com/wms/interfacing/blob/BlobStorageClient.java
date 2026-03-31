package com.wms.interfacing.blob;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.models.BlobListDetails;
import com.azure.storage.blob.models.ListBlobsOptions;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class BlobStorageClient {

    private static final String CONTAINER_NAME = "iffiles";
    private static final DateTimeFormatter PROCESSED_DATE_FMT =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    @Value("${wms.blob.connection-string:}")
    private String connectionString;

    private BlobContainerClient containerClient;

    @PostConstruct
    void init() {
        if (connectionString == null || connectionString.isBlank()) {
            log.warn("Blob Storage connection string is not configured. "
                    + "Blob operations will fail at runtime.");
            return;
        }
        BlobServiceClient serviceClient = new BlobServiceClientBuilder()
                .connectionString(connectionString)
                .buildClient();
        this.containerClient = serviceClient.getBlobContainerClient(CONTAINER_NAME);
    }

    /**
     * pendingフォルダ内のファイル一覧を取得する。
     */
    public List<BlobFileInfo> listPendingFiles(String directory) {
        ensureInitialized();
        String prefix = directory + "/pending/";
        ListBlobsOptions options = new ListBlobsOptions()
                .setPrefix(prefix)
                .setDetails(new BlobListDetails().setRetrieveMetadata(true));

        List<BlobFileInfo> files = new ArrayList<>();
        containerClient.listBlobs(options, null).forEach(item -> {
            String name = item.getName();
            // prefix直下のファイルのみ（サブディレクトリは除外）
            String relativeName = name.substring(prefix.length());
            if (!relativeName.isEmpty() && !relativeName.contains("/")) {
                files.add(toBlobFileInfo(item, relativeName));
            }
        });
        return files;
    }

    /**
     * pendingフォルダからCSVファイルを読み取る。
     */
    public InputStream downloadFile(String directory, String fileName) {
        ensureInitialized();
        String blobPath = directory + "/pending/" + fileName;
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        containerClient.getBlobClient(blobPath).downloadStream(outputStream);
        return new ByteArrayInputStream(outputStream.toByteArray());
    }

    /**
     * ファイルサイズを取得する。
     */
    public long getFileSize(String directory, String fileName) {
        ensureInitialized();
        String blobPath = directory + "/pending/" + fileName;
        return containerClient.getBlobClient(blobPath).getProperties().getBlobSize();
    }

    /**
     * pendingからprocessedフォルダへファイルを移動する。
     *
     * @return 移動先のBlobパス
     */
    public String moveToProcessed(String directory, String fileName) {
        ensureInitialized();
        String sourcePath = directory + "/pending/" + fileName;
        OffsetDateTime now = OffsetDateTime.now();
        String dateDir = String.format("%d/%02d/%02d",
                now.getYear(), now.getMonthValue(), now.getDayOfMonth());
        String timestampPrefix = now.format(PROCESSED_DATE_FMT);
        String destPath = directory + "/processed/" + dateDir + "/"
                + timestampPrefix + "_" + fileName;

        var sourceClient = containerClient.getBlobClient(sourcePath);
        var destClient = containerClient.getBlobClient(destPath);

        // Copy then delete (Azure Blob doesn't have native move)
        String sourceUrl = sourceClient.getBlobUrl();
        destClient.copyFromUrl(sourceUrl);
        sourceClient.delete();

        return destPath;
    }

    private BlobFileInfo toBlobFileInfo(BlobItem item, String fileName) {
        long size = item.getProperties().getContentLength() != null
                ? item.getProperties().getContentLength() : 0;
        OffsetDateTime createdAt = item.getProperties().getCreationTime() != null
                ? item.getProperties().getCreationTime()
                : item.getProperties().getLastModified();
        return new BlobFileInfo(fileName, size, createdAt);
    }

    private void ensureInitialized() {
        if (containerClient == null) {
            throw new IllegalStateException(
                    "BlobStorageClient is not initialized. "
                            + "Check wms.blob.connection-string configuration.");
        }
    }

    public record BlobFileInfo(String fileName, long fileSize, OffsetDateTime createdAt) {
    }
}
