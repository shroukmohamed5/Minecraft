package org.example;

import org.lwjgl.system.MemoryUtil;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import static org.lwjgl.opengl.GL30.*;

public class Mesh {

    private final int vaoId, vboId, eboId;
    private final int vertexCount;

    // 8 نقاط المكعب
    float[] vertices = {
            -0.5f, -0.5f,  0.5f,  // 0 أمام تحت شمال
            0.5f, -0.5f,  0.5f,  // 1 أمام تحت يمين
            0.5f,  0.5f,  0.5f,  // 2 أمام فوق يمين
            -0.5f,  0.5f,  0.5f,  // 3 أمام فوق شمال
            -0.5f, -0.5f, -0.5f,  // 4 خلف تحت شمال
            0.5f, -0.5f, -0.5f,  // 5 خلف تحت يمين
            0.5f,  0.5f, -0.5f,  // 6 خلف فوق يمين
            -0.5f,  0.5f, -0.5f,  // 7 خلف فوق شمال
    };

    // 6 وجوه × 2 مثلث × 3 نقاط
    int[] indices = {
            0,1,2,  2,3,0,  // أمام
            5,4,7,  7,6,5,  // خلف
            4,0,3,  3,7,4,  // شمال
            1,5,6,  6,2,1,  // يمين
            3,2,6,  6,7,3,  // فوق
            4,5,1,  1,0,4,  // تحت
    };

    public Mesh() {
        vertexCount = indices.length;

        vaoId = glGenVertexArrays();
        glBindVertexArray(vaoId);

        vboId = glGenBuffers();
        FloatBuffer vb = MemoryUtil.memAllocFloat(vertices.length);
        vb.put(vertices).flip();
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        glBufferData(GL_ARRAY_BUFFER, vb, GL_STATIC_DRAW);
        MemoryUtil.memFree(vb);

        eboId = glGenBuffers();
        IntBuffer ib = MemoryUtil.memAllocInt(indices.length);
        ib.put(indices).flip();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, eboId);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, ib, GL_STATIC_DRAW);
        MemoryUtil.memFree(ib);

        glVertexAttribPointer(0, 3, GL_FLOAT, false, 0, 0);
        glEnableVertexAttribArray(0);
        glBindVertexArray(0);

        System.out.println("✓ Mesh اتعمل!");
    }

    public void render() {
        glBindVertexArray(vaoId);
        glDrawElements(GL_TRIANGLES, vertexCount, GL_UNSIGNED_INT, 0);
        glBindVertexArray(0);
    }

    public void cleanup() {
        glDeleteBuffers(vboId);
        glDeleteBuffers(eboId);
        glDeleteVertexArrays(vaoId);
    }
}