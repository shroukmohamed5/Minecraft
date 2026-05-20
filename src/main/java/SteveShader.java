package org.example;

import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;
import static org.lwjgl.opengl.GL20.*;

public class SteveShader {

    private final int programId;

    private static final String VERTEX_SRC =
            "#version 330 core\n" +
                    "layout(location = 0) in vec3 aPos;\n" +
                    "layout(location = 1) in vec2 aUV;\n" +
                    "out vec2 vUV;\n" +
                    "uniform mat4 model, view, projection;\n" +
                    "void main() {\n" +
                    "    gl_Position = projection * view * model * vec4(aPos, 1.0);\n" +
                    "    vUV = aUV;\n" +
                    "}\n";

    private static final String FRAGMENT_SRC =
            "#version 330 core\n" +
                    "in vec2 vUV;\n" +
                    "out vec4 FragColor;\n" +
                    "uniform sampler2D uTex;\n" +
                    "uniform float uBright;\n" +
                    "void main() {\n" +
                    "    vec4 col = texture(uTex, vUV);\n" +
                    "    if (col.a < 0.1) discard;\n" +
                    "    FragColor = vec4(col.rgb * uBright, col.a);\n" +
                    "}\n";

    public SteveShader() {
        int vertId = glCreateShader(GL_VERTEX_SHADER);
        glShaderSource(vertId, VERTEX_SRC);
        glCompileShader(vertId);
        if (glGetShaderi(vertId, GL_COMPILE_STATUS) == 0)
            throw new RuntimeException("Vertex: " + glGetShaderInfoLog(vertId));

        int fragId = glCreateShader(GL_FRAGMENT_SHADER);
        glShaderSource(fragId, FRAGMENT_SRC);
        glCompileShader(fragId);
        if (glGetShaderi(fragId, GL_COMPILE_STATUS) == 0)
            throw new RuntimeException("Fragment: " + glGetShaderInfoLog(fragId));

        programId = glCreateProgram();
        glAttachShader(programId, vertId);
        glAttachShader(programId, fragId);
        glLinkProgram(programId);
        if (glGetProgrami(programId, GL_LINK_STATUS) == 0)
            throw new RuntimeException("Link: " + glGetProgramInfoLog(programId));

        glDeleteShader(vertId);
        glDeleteShader(fragId);
    }

    public void setMatrix4f(String name, Matrix4f m) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            glUniformMatrix4fv(glGetUniformLocation(programId, name),
                    false, m.get(stack.mallocFloat(16)));
        }
    }

    public void setInt(String name, int val) {
        glUniform1i(glGetUniformLocation(programId, name), val);
    }

    public void setFloat(String name, float val) {
        glUniform1f(glGetUniformLocation(programId, name), val);
    }

    public void bind()    { glUseProgram(programId); }
    public void unbind()  { glUseProgram(0); }
    public void cleanup() { glDeleteProgram(programId); }
}