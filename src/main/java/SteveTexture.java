package org.example;

import org.lwjgl.system.MemoryStack;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryUtil;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.*;

public class SteveTexture {

    private final int textureId;

    public SteveTexture() {
        textureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureId);

        // إعدادات التيكستشر
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer w   = stack.mallocInt(1);
            IntBuffer h   = stack.mallocInt(1);
            IntBuffer ch  = stack.mallocInt(1);

            // تحميل الصورة من resources
            InputStream is = SteveTexture.class.getResourceAsStream("/Steve.jpg");
            if (is == null) {
                throw new RuntimeException("مش لاقي Steve.jpg في resources!");
            }

            byte[] bytes = is.readAllBytes();
            ByteBuffer raw = MemoryUtil.memAlloc(bytes.length);
            raw.put(bytes).flip();

            STBImage.stbi_set_flip_vertically_on_load(true);
            ByteBuffer img = STBImage.stbi_load_from_memory(raw, w, h, ch, 4);
            MemoryUtil.memFree(raw);

            if (img == null) {
                throw new RuntimeException("فشل تحميل Steve.jpg: " + STBImage.stbi_failure_reason());
            }

            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w.get(0), h.get(0),
                    0, GL_RGBA, GL_UNSIGNED_BYTE, img);
            glGenerateMipmap(GL_TEXTURE_2D);
            STBImage.stbi_image_free(img);

            System.out.println("✓ Steve texture اتحمل! " + w.get(0) + "x" + h.get(0));
        } catch (IOException e) {
            throw new RuntimeException("خطأ في تحميل الصورة: " + e.getMessage());
        }

        glBindTexture(GL_TEXTURE_2D, 0);
    }

    public void bind()    { glBindTexture(GL_TEXTURE_2D, textureId); }
    public void unbind()  { glBindTexture(GL_TEXTURE_2D, 0); }
    public void cleanup() { glDeleteTextures(textureId); }
}