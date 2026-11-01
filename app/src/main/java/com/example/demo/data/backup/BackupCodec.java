package com.example.demo.data.backup;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/** Strict UTF-8 JSON codec for versioned, validated backup files. */
public final class BackupCodec {
    public static final int MAX_BACKUP_BYTES = 5 * 1024 * 1024;

    private static final Gson GSON = new GsonBuilder()
            .serializeNulls()
            .disableHtmlEscaping()
            .create();

    private BackupCodec() {
    }

    public static byte[] encode(BackupData input) {
        BackupData data = BackupData.copyOf(input);
        BackupValidator.validate(data);
        byte[] encoded = GSON.toJson(data).getBytes(StandardCharsets.UTF_8);
        if (encoded.length > MAX_BACKUP_BYTES) {
            throw new IllegalArgumentException("Tệp sao lưu vượt quá giới hạn 5 MiB.");
        }
        return encoded;
    }

    public static BackupData decode(byte[] encoded) {
        if (encoded == null || encoded.length == 0) {
            throw new IllegalArgumentException("Tệp sao lưu rỗng.");
        }
        if (encoded.length > MAX_BACKUP_BYTES) {
            throw new IllegalArgumentException("Tệp sao lưu vượt quá giới hạn 5 MiB.");
        }

        String json = decodeUtf8(encoded);
        try (JsonReader reader = new JsonReader(new StringReader(json))) {
            reader.setStrictness(Strictness.STRICT);
            BackupData parsed = GSON.fromJson(reader, BackupData.class);
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw new IllegalArgumentException("Tệp sao lưu có dữ liệu thừa sau JSON.");
            }
            BackupData data = BackupData.copyOf(parsed);
            BackupValidator.validate(data);
            return data;
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (IOException | JsonParseException | IllegalStateException exception) {
            throw new IllegalArgumentException("Tệp sao lưu không phải JSON hợp lệ.", exception);
        }
    }

    private static String decodeUtf8(byte[] encoded) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(encoded)).toString();
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException("Tệp sao lưu không dùng UTF-8 hợp lệ.", exception);
        }
    }

    /** Reads no more than the configured cap, used by the SAF restore path. */
    public static byte[] readBounded(java.io.InputStream input) throws IOException {
        if (input == null) {
            throw new IOException("Không mở được tệp sao lưu.");
        }
        try (java.io.InputStream source = input;
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8 * 1024];
            int count;
            int total = 0;
            while ((count = source.read(buffer)) != -1) {
                total += count;
                if (total > MAX_BACKUP_BYTES) {
                    throw new IOException("Tệp sao lưu vượt quá giới hạn 5 MiB.");
                }
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        }
    }
}
