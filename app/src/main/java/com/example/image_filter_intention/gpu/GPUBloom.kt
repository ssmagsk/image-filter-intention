package com.example.image_filter_intention.gpu

import android.opengl.EGL14
import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Offscreen GPU bloom (bright-pass + 9-tap blur) using GLES2.
 * Input/Output: RGBA_8888 ByteArray.
 * Note: This uses glReadPixels; for production, render directly to a surface/texture.
 */
class GPUBloom {

    fun applyBloom(rgba: ByteArray, width: Int, height: Int): ByteArray? {
        val egl = EGLEnv(width, height)
        if (!egl.ready) {
            egl.release()
            return null
        }
        val prog = GlProgram(VS, FS_BLOOM)
        if (!prog.ready) {
            prog.release()
            egl.release()
            return null
        }

        // Upload input texture
        val tex = IntArray(1)
        GLES20.glGenTextures(1, tex, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        val inputBuf = ByteBuffer.allocateDirect(rgba.size).order(ByteOrder.nativeOrder())
        inputBuf.put(rgba).position(0)
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D,
            0,
            GLES20.GL_RGBA,
            width,
            height,
            0,
            GLES20.GL_RGBA,
            GLES20.GL_UNSIGNED_BYTE,
            inputBuf
        )

        GLES20.glViewport(0, 0, width, height)
        GLES20.glUseProgram(prog.id)

        GLES20.glEnableVertexAttribArray(prog.aPos)
        GLES20.glVertexAttribPointer(prog.aPos, 2, GLES20.GL_FLOAT, false, 0, QUAD_BUF)
        GLES20.glEnableVertexAttribArray(prog.aTex)
        GLES20.glVertexAttribPointer(prog.aTex, 2, GLES20.GL_FLOAT, false, 0, UV_BUF)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex[0])
        GLES20.glUniform1i(prog.uTex, 0)
        GLES20.glUniform2f(prog.uTexel, 1f / width, 1f / height)
        GLES20.glUniform1f(prog.uThreshold, 0.7f) // bright pass threshold

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        val outBuf = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder())
        GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, outBuf)

        GLES20.glDeleteTextures(1, tex, 0)
        prog.release()
        egl.release()

        val bytes = ByteArray(outBuf.remaining())
        outBuf.get(bytes)
        return bytes
    }

    private class GlProgram(vs: String, fs: String) {
        val id: Int
        val aPos: Int
        val aTex: Int
        val uTex: Int
        val uTexel: Int
        val uThreshold: Int
        val ready: Boolean

        init {
            val vsId = compile(GLES20.GL_VERTEX_SHADER, vs)
            val fsId = compile(GLES20.GL_FRAGMENT_SHADER, fs)
            id = GLES20.glCreateProgram()
            GLES20.glAttachShader(id, vsId)
            GLES20.glAttachShader(id, fsId)
            GLES20.glLinkProgram(id)
            val st = IntArray(1)
            GLES20.glGetProgramiv(id, GLES20.GL_LINK_STATUS, st, 0)
            ready = st[0] == GLES20.GL_TRUE
            aPos = GLES20.glGetAttribLocation(id, "aPosition")
            aTex = GLES20.glGetAttribLocation(id, "aTexCoord")
            uTex = GLES20.glGetUniformLocation(id, "uTex")
            uTexel = GLES20.glGetUniformLocation(id, "uTexel")
            uThreshold = GLES20.glGetUniformLocation(id, "uThreshold")
        }

        fun release() {
            GLES20.glDeleteProgram(id)
        }

        private fun compile(type: Int, src: String): Int {
            val shader = GLES20.glCreateShader(type)
            GLES20.glShaderSource(shader, src)
            GLES20.glCompileShader(shader)
            return shader
        }
    }

    private class EGLEnv(w: Int, h: Int) {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val ready: Boolean
        private val ctx: android.opengl.EGLContext?
        private val surf: android.opengl.EGLSurface?

        init {
            val version = IntArray(2)
            EGL14.eglInitialize(display, version, 0, version, 1)
            val attrib = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<android.opengl.EGLConfig>(1)
            val num = IntArray(1)
            EGL14.eglChooseConfig(display, attrib, 0, configs, 0, 1, num, 0)
            val ctxAttrib = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
            ctx = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT, ctxAttrib, 0)
            val surfAttrib = intArrayOf(
                EGL14.EGL_WIDTH, w,
                EGL14.EGL_HEIGHT, h,
                EGL14.EGL_NONE
            )
            surf = EGL14.eglCreatePbufferSurface(display, configs[0], surfAttrib, 0)
            EGL14.eglMakeCurrent(display, surf, surf, ctx)
            ready = ctx != null && surf != null
        }

        fun release() {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (surf != null) EGL14.eglDestroySurface(display, surf)
            if (ctx != null) EGL14.eglDestroyContext(display, ctx)
            EGL14.eglTerminate(display)
        }
    }

    companion object {
        private val QUAD = floatArrayOf(
            -1f, -1f,
             1f, -1f,
            -1f,  1f,
             1f,  1f
        )
        private val UV = floatArrayOf(
            0f, 1f,
            1f, 1f,
            0f, 0f,
            1f, 0f
        )
        private val QUAD_BUF: FloatBuffer = ByteBuffer.allocateDirect(QUAD.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply { put(QUAD).position(0) }
        private val UV_BUF: FloatBuffer = ByteBuffer.allocateDirect(UV.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply { put(UV).position(0) }

        private const val VS = """
            attribute vec4 aPosition;
            attribute vec2 aTexCoord;
            varying vec2 vTex;
            void main() {
                vTex = aTexCoord;
                gl_Position = aPosition;
            }
        """

        // Bright-pass + 9-tap Gaussian-ish blur, then add original.
        private const val FS_BLOOM = """
            precision mediump float;
            varying vec2 vTex;
            uniform sampler2D uTex;
            uniform vec2 uTexel;
            uniform float uThreshold;
            void main() {
                vec3 orig = texture2D(uTex, vTex).rgb;
                float lum = dot(orig, vec3(0.299, 0.587, 0.114));
                vec3 base = lum > uThreshold ? orig : vec3(0.0);
                vec3 acc = vec3(0.0);
                float w[5];
                w[0]=0.227027; w[1]=0.1945946; w[2]=0.1216216; w[3]=0.054054; w[4]=0.016216;
                acc += w[0]*base;
                for (int i=1; i<5; ++i) {
                    vec2 off = uTexel * float(i);
                    acc += w[i]*texture2D(uTex, vTex + vec2(off.x, 0.0)).rgb;
                    acc += w[i]*texture2D(uTex, vTex - vec2(off.x, 0.0)).rgb;
                    acc += w[i]*texture2D(uTex, vTex + vec2(0.0, off.y)).rgb;
                    acc += w[i]*texture2D(uTex, vTex - vec2(0.0, off.y)).rgb;
                }
                vec3 outColor = clamp(orig + acc, 0.0, 1.0);
                gl_FragColor = vec4(outColor, 1.0);
            }
        """
    }
}

