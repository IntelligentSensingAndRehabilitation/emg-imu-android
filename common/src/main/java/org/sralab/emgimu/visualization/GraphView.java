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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * This class uses external library AChartEngine to show dynamic real time line graph for HR values
 */
public class GraphView extends GLSurfaceView {

	public GraphView(Context context) {
		this(context, null, 0);
	}

	public GraphView(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public class Triangle {

		private FloatBuffer vertexBuffer;

		// number of coordinates per vertex in this array
		static final int COORDS_PER_VERTEX = 3;
		float triangleCoords[] = {   // in counterclockwise order:
				0.0f,  0.622008459f, 0.0f, // top
				-0.5f, -0.311004243f, 0.0f, // bottom left
				0.5f, -0.311004243f, 0.0f  // bottom right
		};

		// Set color with red, green, blue and alpha (opacity) values
		float color[] = { 0.63671875f, 0.76953125f, 0.22265625f, 1.0f };
		private final int mProgram;

		public Triangle() {
			// initialize vertex byte buffer for shape coordinates
			ByteBuffer bb = ByteBuffer.allocateDirect(
					// (number of coordinate values * 4 bytes per float)
					triangleCoords.length * 4);
			// use the device hardware's native byte order
			bb.order(ByteOrder.nativeOrder());

			// create a floating point buffer from the ByteBuffer
			vertexBuffer = bb.asFloatBuffer();
			// add the coordinates to the FloatBuffer
			vertexBuffer.put(triangleCoords);
			// set the buffer to read the first coordinate
			vertexBuffer.position(0);

			int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER,
					vertexShaderCode);
			int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER,
					fragmentShaderCode);

			// create empty OpenGL ES Program
			mProgram = GLES20.glCreateProgram();

			// add the vertex shader to program
			GLES20.glAttachShader(mProgram, vertexShader);

			// add the fragment shader to program
			GLES20.glAttachShader(mProgram, fragmentShader);

			// creates OpenGL ES program executables
			GLES20.glLinkProgram(mProgram);
		}

		private final String vertexShaderCode =
				// This matrix member variable provides a hook to manipulate
				// the coordinates of the objects that use this vertex shader
				"uniform mat4 uMVPMatrix;" +
						"attribute vec4 vPosition;" +
						"void main() {" +
						// the matrix must be included as a modifier of gl_Position
						// Note that the uMVPMatrix factor *must be first* in order
						// for the matrix multiplication product to be correct.
						"  gl_Position = uMVPMatrix * vPosition;" +
						"}";


		private final String fragmentShaderCode =
				"precision mediump float;" +
						"uniform vec4 vColor;" +
						"void main() {" +
						"  gl_FragColor = vColor;" +
						"}";

		private int positionHandle;
		private int colorHandle;

		private final int vertexCount = triangleCoords.length / COORDS_PER_VERTEX;
		private final int vertexStride = COORDS_PER_VERTEX * 4; // 4 bytes per vertex
		private int vPMatrixHandle;

		public void draw(float[] mvpMatrix) {
			// Add program to OpenGL ES environment
			GLES20.glUseProgram(mProgram);

			// get handle to vertex shader's vPosition member
			positionHandle = GLES20.glGetAttribLocation(mProgram, "vPosition");

			// Enable a handle to the triangle vertices
			GLES20.glEnableVertexAttribArray(positionHandle);

			// Prepare the triangle coordinate data
			GLES20.glVertexAttribPointer(positionHandle, COORDS_PER_VERTEX,
					GLES20.GL_FLOAT, false,
					vertexStride, vertexBuffer);

			// get handle to fragment shader's vColor member
			colorHandle = GLES20.glGetUniformLocation(mProgram, "vColor");

			// Set color for drawing the triangle
			GLES20.glUniform4fv(colorHandle, 1, color, 0);

			// get handle to shape's transformation matrix
			vPMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix");

			// Pass the projection and view transformation to the shader
			GLES20.glUniformMatrix4fv(vPMatrixHandle, 1, false, mvpMatrix, 0);

			// Draw the triangle
			GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount);

			// Disable vertex array
			GLES20.glDisableVertexAttribArray(positionHandle);
		}

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

	public GraphView(Context context, AttributeSet attrs, int defStyle) {
		super(context);
		setEGLContextClientVersion(2);
		setZOrderOnTop(true);
		setEGLConfigChooser(8, 8, 8, 8, 16, 0);
		getHolder().setFormat(PixelFormat.TRANSLUCENT);

		setRenderer(new GLSurfaceView.Renderer() {

			private Triangle mTriangle;
			private Line mLine;

			// vPMatrix is an abbreviation for "Model View Projection Matrix"
			private final float[] vPMatrix = new float[16];
			private final float[] projectionMatrix = new float[16];
			private final float[] viewMatrix = new float[16];

			@Override
			public void onSurfaceCreated(GL10 unused, EGLConfig config) {

				GLES20.glLineWidth(15.0f);

				mTriangle = new Triangle();
				mLine = new Line();
				mLine.SetVerts(0.1f, 0.1f, 0.2f, 0.3f, 0.5f, 0f);
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
		setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
	}
	public class Line {
		private FloatBuffer VertexBuffer;

		private final String VertexShaderCode =
				// This matrix member variable provides a hook to manipulate
				// the coordinates of the objects that use this vertex shader
				"uniform mat4 uMVPMatrix;" +
						"attribute vec4 vPosition;" +
						"void main() {" +
						// the matrix must be included as a modifier of gl_Position
						"  gl_Position = uMVPMatrix * vPosition;" +
						"}";

		private final String FragmentShaderCode =
				"precision mediump float;" +
						"uniform vec4 vColor;" +
						"void main() {" +
						"  gl_FragColor = vColor;" +
						"}";

		protected int GlProgram;
		protected int PositionHandle;
		protected int ColorHandle;
		protected int MVPMatrixHandle;

		// number of coordinates per vertex in this array
		static final int COORDS_PER_VERTEX = 3;
		float LineCoords[] = {
				-0.4f, -0.2f, 0.0f,
				0.2f, 0.4f, 0.5f
		};

		private final int VertexCount = LineCoords.length / COORDS_PER_VERTEX;
		private final int VertexStride = COORDS_PER_VERTEX * 4; // 4 bytes per vertex

		// Set color with red, green, blue and alpha (opacity) values
		float color[] = { 0.0f, 0.0f, 0.0f, 1.0f };

		public Line() {
			// initialize vertex byte buffer for shape coordinates
			ByteBuffer bb = ByteBuffer.allocateDirect(
					// (number of coordinate values * 4 bytes per float)
					LineCoords.length * 4);
			// use the device hardware's native byte order
			bb.order(ByteOrder.nativeOrder());

			// create a floating point buffer from the ByteBuffer
			VertexBuffer = bb.asFloatBuffer();
			// add the coordinates to the FloatBuffer
			VertexBuffer.put(LineCoords);
			// set the buffer to read the first coordinate
			VertexBuffer.position(0);

			int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, VertexShaderCode);
			int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, FragmentShaderCode);

			GlProgram = GLES20.glCreateProgram();             // create empty OpenGL ES Program
			GLES20.glAttachShader(GlProgram, vertexShader);   // add the vertex shader to program
			GLES20.glAttachShader(GlProgram, fragmentShader); // add the fragment shader to program
			GLES20.glLinkProgram(GlProgram);                  // creates OpenGL ES program executables
		}

		public void SetVerts(float v0, float v1, float v2, float v3, float v4, float v5) {
			LineCoords[0] = v0;
			LineCoords[1] = v1;
			LineCoords[2] = v2;
			LineCoords[3] = v3;
			LineCoords[4] = v4;
			LineCoords[5] = v5;

			VertexBuffer.put(LineCoords);
			// set the buffer to read the first coordinate
			VertexBuffer.position(0);
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
			PositionHandle = GLES20.glGetAttribLocation(GlProgram, "vPosition");

			// Enable a handle to the triangle vertices
			GLES20.glEnableVertexAttribArray(PositionHandle);

			// Prepare the triangle coordinate data
			GLES20.glVertexAttribPointer(PositionHandle, COORDS_PER_VERTEX,
					GLES20.GL_FLOAT, false,
					VertexStride, VertexBuffer);

			// get handle to fragment shader's vColor member
			ColorHandle = GLES20.glGetUniformLocation(GlProgram, "vColor");

			// Set color for drawing the triangle
			GLES20.glUniform4fv(ColorHandle, 1, color, 0);

			// get handle to shape's transformation matrix
			MVPMatrixHandle = GLES20.glGetUniformLocation(GlProgram, "uMVPMatrix");

			// Apply the projection and view transformation
			GLES20.glUniformMatrix4fv(MVPMatrixHandle, 1, false, mvpMatrix, 0);

			// Draw the triangle
			GLES20.glDrawArrays(GLES20.GL_LINES, 0, VertexCount);

			// Disable vertex array
			GLES20.glDisableVertexAttribArray(PositionHandle);
		}
	}
}
