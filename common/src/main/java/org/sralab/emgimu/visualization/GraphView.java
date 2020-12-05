/*
 * Copyright (c) 2015, Nordic Semiconductor
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.sralab.emgimu.visualization;

import android.content.Context;
import android.graphics.PixelFormat;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.util.AttributeSet;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class GraphView extends GLSurfaceView {


	// Handle for object to draw
	private Line mLine;

	public Line getLine() { return mLine; }

	public GraphView(Context context) {
		super(context);
		init();
	}

	public GraphView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	private void init() {

		setEGLContextClientVersion(2);
		setZOrderOnTop(true);
		setEGLConfigChooser(8, 8, 8, 8, 16, 0);
		getHolder().setFormat(PixelFormat.TRANSLUCENT);

		setRenderer(new GLSurfaceView.Renderer() {

			// vPMatrix is an abbreviation for "Model View Projection Matrix"
			private final float[] vPMatrix = new float[16];
			private final float[] projectionMatrix = new float[16];
			private final float[] viewMatrix = new float[16];

			@Override
			public void onSurfaceCreated(GL10 unused, EGLConfig config) {

				GLES20.glLineWidth(15.0f);


				mLine = new Line(100);


				//mLine.SetVerts(-1.1f, -1.1f, 0.0f, 1.0f, 1.0f, 0.0f);
				mLine.SetColor(.8f, .8f, 0f, 1.0f);
			}

			@Override
			public void onSurfaceChanged(GL10 unused, int width, int height) {
				GLES20.glViewport(0, 0, width, height);
				float ratio = (float) width / height;

				// this projection matrix is applied to object coordinates
				// in the onDrawFrame() method
				Matrix.frustumM(projectionMatrix, 0, -ratio, ratio, -1, 1, 3, 7);
			}

			@Override
			public void onDrawFrame(GL10 unused) {
				GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

				// Set the camera position (View matrix)
				Matrix.setLookAtM(viewMatrix, 0, 0, 0, -3, 0f, 0f, 0f, 0f, 1.0f, 0.0f);

				// Calculate the projection and view transformation
				Matrix.multiplyMM(vPMatrix, 0, projectionMatrix, 0, viewMatrix, 0);

				//mTriangle.draw(vPMatrix);
				mLine.draw(vPMatrix);
			}

		});
		//setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
	}

	public static int loadShader(int type, String shaderCode){

		// create a vertex shader type (GLES20.GL_VERTEX_SHADER)
		// or a fragment shader type (GLES20.GL_FRAGMENT_SHADER)
		int shader = GLES20.glCreateShader(type);

		// add the source code to the shader and compile it
		GLES20.glShaderSource(shader, shaderCode);
		GLES20.glCompileShader(shader);

		return shader;
	}

	public class Line {

		private final String VertexShaderCode =
				// This matrix member variable provides a hook to manipulate
				// the coordinates of the objects that use this vertex shader
				"uniform mat4 uMVPMatrix;" +
						"attribute float vXPosition;" +
						"attribute float vYPosition;" +
						"void main() {" +
						// Use this to apply an OpenGL perspective
						//"  vec4 vPosition = vec4(vXPosition, vYPosition, 0, 1);" +
						//"  //gl_Position = uMVPMatrix * vPosition;" +
						"  gl_Position = vec4(vXPosition, vYPosition, 0, 1);" +
						"}";

		private final String FragmentShaderCode =
				"precision mediump float;" +
						"uniform vec4 vColor;" +
						"void main() {" +
						"  gl_FragColor = vColor;" +
						"}";

		protected int GlProgram;
		protected int XPositionHandle;
		protected int YPositionHandle;
		protected int ColorHandle;
		protected int MVPMatrixHandle;

		float [] XCoords;
		float [] YCoords;

		private FloatBuffer XBuffer;
		private FloatBuffer YBuffer;

		// Set color with red, green, blue and alpha (opacity) values
		float color[] = { 0.0f, 0.0f, 0.0f, 1.0f };

		int idx = 0;

		public void update(float val) {
			Log.d("GraphView", "Update: " + val);
			YCoords[idx] = val;
			idx = (idx + 1) % YCoords.length;
			YBuffer.put(YCoords);
			YBuffer.position(0);
		}

		public Line(int N) {

			XCoords = new float[N];
			YCoords = new float[N];
			for (int i = 0; i < N; i++) {
				XCoords[i] = (float) i * 2.0f / (float) N - 1.0f;
				YCoords[i] = 0.1f * (i % 10);
			}

			// initialize vertex byte buffer for shape coordinates
			ByteBuffer bb = ByteBuffer.allocateDirect(XCoords.length * 4);
			// use the device hardware's native byte order
			bb.order(ByteOrder.nativeOrder());

			// create a floating point buffer from the ByteBuffer
			XBuffer = bb.asFloatBuffer();
			// add the coordinates to the FloatBuffer
			XBuffer.put(XCoords);
			// set the buffer to read the first coordinate
			XBuffer.position(0);

			// initialize vertex byte buffer for shape coordinates
			bb = ByteBuffer.allocateDirect(YCoords.length * 4);
			// use the device hardware's native byte order
			bb.order(ByteOrder.nativeOrder());

			// create a floating point buffer from the ByteBuffer
			YBuffer = bb.asFloatBuffer();
			// add the coordinates to the FloatBuffer
			YBuffer.put(YCoords);
			// set the buffer to read the first coordinate
			YBuffer.position(0);

			int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, VertexShaderCode);
			int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, FragmentShaderCode);

			GlProgram = GLES20.glCreateProgram();             // create empty OpenGL ES Program
			GLES20.glAttachShader(GlProgram, vertexShader);   // add the vertex shader to program
			GLES20.glAttachShader(GlProgram, fragmentShader); // add the fragment shader to program
			GLES20.glLinkProgram(GlProgram);                  // creates OpenGL ES program executables
		}

		public void SetColor(float red, float green, float blue, float alpha) {
			color[0] = red;
			color[1] = green;
			color[2] = blue;
			color[3] = alpha;
		}

		public void draw(float[] mvpMatrix) {
			// Add program to OpenGL ES environment
			GLES20.glUseProgram(GlProgram);

			// get handle to vertex shader's vPosition member
			XPositionHandle = GLES20.glGetAttribLocation(GlProgram, "vXPosition");

			// Enable a handle for the horizontal coordinates
			GLES20.glEnableVertexAttribArray(XPositionHandle);

			// Load the horizontal coordinates
			GLES20.glVertexAttribPointer(XPositionHandle, 1, GLES20.GL_FLOAT,
					false, 0, XBuffer);

			// get handle to vertex shader's vPosition member
			YPositionHandle = GLES20.glGetAttribLocation(GlProgram, "vYPosition");

			// Enable a handle for the horizontal coordinates
			GLES20.glEnableVertexAttribArray(YPositionHandle);

			// Load the horizontal coordinates
			GLES20.glVertexAttribPointer(YPositionHandle, 1, GLES20.GL_FLOAT,
					false, 0, YBuffer);

			// get handle to fragment shader's vColor member
			ColorHandle = GLES20.glGetUniformLocation(GlProgram, "vColor");

			// Set color for drawing the triangle
			GLES20.glUniform4fv(ColorHandle, 1, color, 0);

			// get handle to shape's transformation matrix
			MVPMatrixHandle = GLES20.glGetUniformLocation(GlProgram, "uMVPMatrix");

			// Apply the projection and view transformation
			GLES20.glUniformMatrix4fv(MVPMatrixHandle, 1, false, mvpMatrix, 0);

			// Draw the line
			GLES20.glDrawArrays(GLES20.GL_LINE_STRIP, 0, XCoords.length);

			// Disable vertex array
			GLES20.glDisableVertexAttribArray(XPositionHandle);
			GLES20.glDisableVertexAttribArray(YPositionHandle);
		}
	}
}
