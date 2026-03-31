package com.wms.interfacing.blob;

import com.azure.core.http.rest.PagedIterable;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.models.BlobItemProperties;
import com.azure.storage.blob.models.ListBlobsOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("BlobStorageClient")
class BlobStorageClientTest {

    private static BlobContainerClient createMockContainerClient() {
        return mock(BlobContainerClient.class);
    }

    private static BlobStorageClient createInitializedClient(BlobContainerClient containerClient) {
        BlobStorageClient client = new BlobStorageClient();
        try {
            java.lang.reflect.Field field = BlobStorageClient.class.getDeclaredField("containerClient");
            field.setAccessible(true);
            field.set(client, containerClient);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return client;
    }

    @Nested
    @DisplayName("ensureInitialized — 未初期化状態")
    class EnsureInitialized {

        @Test
        @DisplayName("未初期化状態でlistPendingFilesを呼ぶとIllegalStateException")
        void listPendingFiles_notInitialized_throwsException() {
            BlobStorageClient uninitClient = new BlobStorageClient();

            assertThatThrownBy(() -> uninitClient.listPendingFiles("inbound-plan"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("not initialized");
        }

        @Test
        @DisplayName("未初期化状態でdownloadFileを呼ぶとIllegalStateException")
        void downloadFile_notInitialized_throwsException() {
            BlobStorageClient uninitClient = new BlobStorageClient();

            assertThatThrownBy(() -> uninitClient.downloadFile("inbound-plan", "test.csv"))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("未初期化状態でgetFileSizeを呼ぶとIllegalStateException")
        void getFileSize_notInitialized_throwsException() {
            BlobStorageClient uninitClient = new BlobStorageClient();

            assertThatThrownBy(() -> uninitClient.getFileSize("inbound-plan", "test.csv"))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("未初期化状態でmoveToProcessedを呼ぶとIllegalStateException")
        void moveToProcessed_notInitialized_throwsException() {
            BlobStorageClient uninitClient = new BlobStorageClient();

            assertThatThrownBy(() -> uninitClient.moveToProcessed("inbound-plan", "test.csv"))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("init")
    class Init {

        @Test
        @DisplayName("connectionStringが空の場合、containerClientはnullのまま")
        void init_emptyConnectionString_containerClientNull() {
            BlobStorageClient emptyClient = new BlobStorageClient();
            // connectionStringのデフォルトは空文字列（@Value default）
            // init()はcontainerClientをnullのままにする
            // → ensureInitializedでIllegalStateException
            assertThatThrownBy(() -> emptyClient.listPendingFiles("test"))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("connectionStringがnullの場合、containerClientはnullのまま")
        void init_nullConnectionString_containerClientNull() throws Exception {
            BlobStorageClient nullClient = new BlobStorageClient();
            java.lang.reflect.Field csField = BlobStorageClient.class.getDeclaredField("connectionString");
            csField.setAccessible(true);
            csField.set(nullClient, null);
            // init() はnullガードされている
            nullClient.getClass().getDeclaredMethod("init").setAccessible(true);
            java.lang.reflect.Method initMethod = BlobStorageClient.class.getDeclaredMethod("init");
            initMethod.setAccessible(true);
            initMethod.invoke(nullClient);

            assertThatThrownBy(() -> nullClient.listPendingFiles("test"))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("listPendingFiles — 初期化済み")
    class ListPendingFiles {

        private BlobContainerClient containerClient;
        private BlobStorageClient client;

        @BeforeEach
        void setUp() {
            containerClient = createMockContainerClient();
            client = createInitializedClient(containerClient);
        }

        @Test
        @DisplayName("正常系 — ファイル一覧を返す")
        void listPendingFiles_returnsFiles() {
            BlobItem item1 = createBlobItem("inbound-plan/pending/file1.csv", 1024L,
                    OffsetDateTime.now(), null);
            BlobItem item2 = createBlobItem("inbound-plan/pending/file2.csv", 2048L,
                    null, OffsetDateTime.now());

            @SuppressWarnings("unchecked")
            PagedIterable<BlobItem> pagedIterable = mock(PagedIterable.class);
            doAnswer(inv -> {
                java.util.function.Consumer<BlobItem> consumer = inv.getArgument(0);
                consumer.accept(item1);
                consumer.accept(item2);
                return null;
            }).when(pagedIterable).forEach(any());
            when(containerClient.listBlobs(any(ListBlobsOptions.class), isNull()))
                    .thenReturn(pagedIterable);

            List<BlobStorageClient.BlobFileInfo> result = client.listPendingFiles("inbound-plan");

            assertThat(result).hasSize(2);
            assertThat(result.get(0).fileName()).isEqualTo("file1.csv");
            assertThat(result.get(0).fileSize()).isEqualTo(1024);
            assertThat(result.get(1).fileName()).isEqualTo("file2.csv");
            assertThat(result.get(1).fileSize()).isEqualTo(2048);
        }

        @Test
        @DisplayName("正常系 — サブディレクトリは除外される")
        void listPendingFiles_excludesSubdirectories() {
            BlobItem fileItem = createBlobItem("inbound-plan/pending/file.csv", 100L,
                    OffsetDateTime.now(), null);
            BlobItem dirItem = createBlobItem("inbound-plan/pending/subdir/file.csv", 200L,
                    OffsetDateTime.now(), null);

            @SuppressWarnings("unchecked")
            PagedIterable<BlobItem> pagedIterable = mock(PagedIterable.class);
            doAnswer(inv -> {
                java.util.function.Consumer<BlobItem> consumer = inv.getArgument(0);
                consumer.accept(fileItem);
                consumer.accept(dirItem);
                return null;
            }).when(pagedIterable).forEach(any());
            when(containerClient.listBlobs(any(ListBlobsOptions.class), isNull()))
                    .thenReturn(pagedIterable);

            List<BlobStorageClient.BlobFileInfo> result = client.listPendingFiles("inbound-plan");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).fileName()).isEqualTo("file.csv");
        }

        @Test
        @DisplayName("正常系 — プレフィックスと同名のblobは除外される")
        void listPendingFiles_excludesPrefixItself() {
            // blob name = "inbound-plan/pending/" → relativeName = "" → excluded
            BlobItem emptyNameItem = createBlobItem("inbound-plan/pending/", 0L,
                    OffsetDateTime.now(), null);

            @SuppressWarnings("unchecked")
            PagedIterable<BlobItem> pagedIterable = mock(PagedIterable.class);
            doAnswer(inv -> {
                java.util.function.Consumer<BlobItem> consumer = inv.getArgument(0);
                consumer.accept(emptyNameItem);
                return null;
            }).when(pagedIterable).forEach(any());
            when(containerClient.listBlobs(any(ListBlobsOptions.class), isNull()))
                    .thenReturn(pagedIterable);

            List<BlobStorageClient.BlobFileInfo> result = client.listPendingFiles("inbound-plan");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("正常系 — 空のフォルダで空リスト")
        void listPendingFiles_emptyFolder_returnsEmptyList() {
            @SuppressWarnings("unchecked")
            PagedIterable<BlobItem> pagedIterable = mock(PagedIterable.class);
            doAnswer(inv -> null).when(pagedIterable).forEach(any());
            when(containerClient.listBlobs(any(ListBlobsOptions.class), isNull()))
                    .thenReturn(pagedIterable);

            List<BlobStorageClient.BlobFileInfo> result = client.listPendingFiles("inbound-plan");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("正常系 — contentLengthがnullの場合は0になる")
        void listPendingFiles_nullContentLength_returnsZeroSize() {
            BlobItem item = createBlobItem("inbound-plan/pending/file.csv", null,
                    null, null);

            @SuppressWarnings("unchecked")
            PagedIterable<BlobItem> pagedIterable = mock(PagedIterable.class);
            doAnswer(inv -> {
                java.util.function.Consumer<BlobItem> consumer = inv.getArgument(0);
                consumer.accept(item);
                return null;
            }).when(pagedIterable).forEach(any());
            when(containerClient.listBlobs(any(ListBlobsOptions.class), isNull()))
                    .thenReturn(pagedIterable);

            List<BlobStorageClient.BlobFileInfo> result = client.listPendingFiles("inbound-plan");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).fileSize()).isEqualTo(0);
            // creationTime=null, lastModified=null → createdAt = null
            assertThat(result.get(0).createdAt()).isNull();
        }
    }

    @Nested
    @DisplayName("downloadFile — 初期化済み")
    class DownloadFile {

        private BlobContainerClient containerClient;
        private BlobStorageClient client;

        @BeforeEach
        void setUp() {
            containerClient = createMockContainerClient();
            client = createInitializedClient(containerClient);
        }

        @Test
        @DisplayName("正常系 — ファイル内容をInputStreamとして返す")
        void downloadFile_returnsInputStream() throws Exception {
            BlobClient blobClient = mock(BlobClient.class);
            when(containerClient.getBlobClient("inbound-plan/pending/test.csv"))
                    .thenReturn(blobClient);
            doAnswer(inv -> {
                ByteArrayOutputStream os = inv.getArgument(0);
                os.write("header\ndata\n".getBytes());
                return null;
            }).when(blobClient).downloadStream(any(java.io.OutputStream.class));

            InputStream result = client.downloadFile("inbound-plan", "test.csv");

            byte[] bytes = result.readAllBytes();
            assertThat(new String(bytes)).isEqualTo("header\ndata\n");
        }
    }

    @Nested
    @DisplayName("getFileSize — 初期化済み")
    class GetFileSize {

        private BlobContainerClient containerClient;
        private BlobStorageClient client;

        @BeforeEach
        void setUp() {
            containerClient = createMockContainerClient();
            client = createInitializedClient(containerClient);
        }

        @Test
        @DisplayName("正常系 — ファイルサイズを返す")
        void getFileSize_returnsSize() {
            BlobClient blobClient = mock(BlobClient.class);
            com.azure.storage.blob.models.BlobProperties properties =
                    mock(com.azure.storage.blob.models.BlobProperties.class);
            when(containerClient.getBlobClient("inbound-plan/pending/test.csv"))
                    .thenReturn(blobClient);
            when(blobClient.getProperties()).thenReturn(properties);
            when(properties.getBlobSize()).thenReturn(51200L);

            long size = client.getFileSize("inbound-plan", "test.csv");

            assertThat(size).isEqualTo(51200L);
        }
    }

    @Nested
    @DisplayName("moveToProcessed — 初期化済み")
    class MoveToProcessed {

        private BlobContainerClient containerClient;
        private BlobStorageClient client;

        @BeforeEach
        void setUp() {
            containerClient = createMockContainerClient();
            client = createInitializedClient(containerClient);
        }

        @Test
        @DisplayName("正常系 — ファイルをpendingからprocessedへ移動する")
        void moveToProcessed_copiesAndDeletes() {
            BlobClient sourceClient = mock(BlobClient.class);
            BlobClient destClient = mock(BlobClient.class);
            when(containerClient.getBlobClient(eq("inbound-plan/pending/test.csv")))
                    .thenReturn(sourceClient);
            when(containerClient.getBlobClient(
                    org.mockito.ArgumentMatchers.startsWith("inbound-plan/processed/")))
                    .thenReturn(destClient);
            when(sourceClient.getBlobUrl()).thenReturn("https://blob.core/pending/test.csv");

            String destPath = client.moveToProcessed("inbound-plan", "test.csv");

            assertThat(destPath).startsWith("inbound-plan/processed/");
            assertThat(destPath).contains("test.csv");
            verify(destClient).copyFromUrl("https://blob.core/pending/test.csv");
            verify(sourceClient).delete();
        }
    }

    @Nested
    @DisplayName("BlobFileInfo")
    class BlobFileInfoTest {

        @Test
        @DisplayName("recordの各フィールドにアクセスできる")
        void blobFileInfo_fieldsAccessible() {
            var info = new BlobStorageClient.BlobFileInfo("file.csv", 1024, null);
            assertThat(info.fileName()).isEqualTo("file.csv");
            assertThat(info.fileSize()).isEqualTo(1024);
            assertThat(info.createdAt()).isNull();
        }
    }

    // --- Helper ---

    private BlobItem createBlobItem(String name, Long contentLength,
                                     OffsetDateTime creationTime,
                                     OffsetDateTime lastModified) {
        BlobItem item = mock(BlobItem.class);
        BlobItemProperties props = mock(BlobItemProperties.class);
        when(item.getName()).thenReturn(name);
        when(item.getProperties()).thenReturn(props);
        when(props.getContentLength()).thenReturn(contentLength);
        when(props.getCreationTime()).thenReturn(creationTime);
        when(props.getLastModified()).thenReturn(lastModified);
        return item;
    }
}
