/*
 * zorbage-cryoem: : code for populating cryo em file data into zorbage structures for further processing
 *
 * Copyright (C) 2025 Barry DeZonia
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package nom.bdezonia.zorbage.cryoem;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;

import nom.bdezonia.zorbage.algebra.Algebra;
import nom.bdezonia.zorbage.algebra.Allocatable;
import nom.bdezonia.zorbage.algebra.G;
import nom.bdezonia.zorbage.coordinates.LinearNdCoordinateSpace;
import nom.bdezonia.zorbage.data.DimensionedDataSource;
import nom.bdezonia.zorbage.data.DimensionedStorage;
import nom.bdezonia.zorbage.misc.DataBundle;
import nom.bdezonia.zorbage.procedure.Procedure3;
import nom.bdezonia.zorbage.sampling.IntegerIndex;
import nom.bdezonia.zorbage.type.color.RgbMember;
import nom.bdezonia.zorbage.type.complex.float32.ComplexFloat32Member;
import nom.bdezonia.zorbage.type.gaussian.int16.GaussianInt16Member;
import nom.bdezonia.zorbage.type.integer.int16.SignedInt16Member;
import nom.bdezonia.zorbage.type.integer.int16.UnsignedInt16Member;
import nom.bdezonia.zorbage.type.integer.int4.SignedInt4Member;
import nom.bdezonia.zorbage.type.integer.int4.UnsignedInt4Member;
import nom.bdezonia.zorbage.type.integer.int8.SignedInt8Member;
import nom.bdezonia.zorbage.type.integer.int8.UnsignedInt8Member;
import nom.bdezonia.zorbage.type.real.float16.Float16Member;
import nom.bdezonia.zorbage.type.real.float32.Float32Member;

/**
 * @author Barry DeZonia
 */
public class MrcReader {

	// do not instantiate
	
	private MrcReader() { }
	
	/**
	 * 
	 * @param filename
	 * @return
	 */
	public static
	
		DataBundle
		
			readAllDatasets(String filename)
	{
		try {
		
			URI uri = new URI("file", null, new File(filename).getAbsolutePath(), null);
			
			return readAllDatasets(uri);
	
		} catch (URISyntaxException e) {
			
			throw new IllegalArgumentException("Bad name for file: "+e.getMessage());
		}
	}

	/**
	 * 
	 * @param <T>
	 * @param <U>
	 * @param fileURI
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public static <T extends Algebra<T,U>, U extends Allocatable<U>>
	
		DataBundle
			
			readAllDatasets(URI fileURI)
	{
		DataBundle bundle = new DataBundle();
	    
		DimensionedDataSource<U> data = null;

		byte[] header = null;
		
		@SuppressWarnings("unused")
		byte[] extendedHeader = null;
		
		final boolean littleEndian;
		
		int dataType = -1;
		
		boolean signedBytes = false;
		
		boolean swapOriginSign = false;
		
		int[] axisOrder = new int[3];
		
		long[] dims = new long[3];
		
		BigDecimal[] origins = new BigDecimal[3];
		
		BigDecimal[] spacings = new BigDecimal[3];
		
		try {
			
			InputStream f1 = fileURI.toURL().openStream();
			
			BufferedInputStream bf1 = new BufferedInputStream(f1);
			
			DataInputStream dis = new DataInputStream(bf1);
	
			header = dis.readNBytes(1024);
	
			littleEndian = (header[212] == 68 && header[212] == 65);
			
			int extendedSize = decodeInt(header, 92, littleEndian);
			
			extendedHeader = dis.readNBytes(extendedSize);
			
			Algebra<T,U> alg = null;
			
			Procedure3<Boolean, byte[], U> byteTransformer = null;
	
			float originX = 0;
			float originY = 0;
			float originZ = 0;
			
			float cellSpacingX = 1;
			float cellSpacingY = 1;
			float cellSpacingZ = 1;
			
			boolean isImodHeader =
					
					decodeInt(header, 152, littleEndian) == 1146047817;
			
			int flags = decodeInt(header, 156, littleEndian);

			signedBytes = isImodHeader && (flags & 1) == 1;
					
			swapOriginSign = isImodHeader && (flags & 4) == 4;
			
			dataType = decodeInt(header, 12, littleEndian);
	
			double bytesPerElement = 0;
			
			switch (dataType) {
		        
				case 0: // Signed8Bit or Unsigned8Bit
					
					bytesPerElement = 1;
			        
					if (signedBytes) {
						
						alg = (T) G.INT8;
			            
						byteTransformer = new Procedure3<Boolean, byte[], U>() {
							@Override
							public void call(Boolean evenPixel, byte[] data, U type) {
								
								byte v = data[0];
								
								((SignedInt8Member) type).setV(v);
							}
						};
					}
					else {  // unsigned bytes
			            
						alg = (T) G.UINT8;
			            
						byteTransformer = new Procedure3<Boolean, byte[], U>() {
							@Override
							public void call(Boolean evenPixel, byte[] data, U type) {
								
								byte v = data[0];
								
								((UnsignedInt8Member) type).setV(v);
							}
						};
					}
		            
					break;
		            
				case 1: // Signed16Bit
		        
					bytesPerElement = 2;
			        
					alg = (T) G.INT16;
		            
					byteTransformer = new Procedure3<Boolean, byte[], U>() {
						@Override
						public void call(Boolean evenPixel, byte[] data, U type) {
							
							short v = decodeShort(data, 0, littleEndian);
							
							((SignedInt16Member) type).setV(v);
						}
					};
		            
					break;
		            
	
				case 2: // Float32Bit
	
					bytesPerElement = 4;
					
					alg = (T) G.FLT;
		            
					byteTransformer = new Procedure3<Boolean, byte[], U>() {
						@Override
						public void call(Boolean evenPixel, byte[] data, U type) {
							
							float v = decodeFloat(data, 0, littleEndian);
							
							((Float32Member) type).setV(v);
						}
					};
		            
					break;
	
				case 3: // Gaussian16Bit
	
					bytesPerElement = 4;
			        
					alg = (T) G.GAUSS16;
		            
					byteTransformer = new Procedure3<Boolean, byte[], U>() {
						@Override
						public void call(Boolean evenPixel, byte[] data, U type) {
							
							short v1 = decodeShort(data, 0, littleEndian);
							short v2 = decodeShort(data, 2, littleEndian);
							
							((GaussianInt16Member) type).setR(v1);
							((GaussianInt16Member) type).setI(v2);
						}
					};
		            
					break;
		            
				case 4: // ComplexFloat32Bit:
		        
					bytesPerElement = 8;
			        
					alg = (T) G.CFLT;
		            
					byteTransformer = new Procedure3<Boolean, byte[], U>() {
						@Override
						public void call(Boolean evenPixel, byte[] data, U type) {
							
							float v1 = decodeFloat(data, 0, littleEndian);						
							float v2 = decodeFloat(data, 4, littleEndian);						
							
							((ComplexFloat32Member) type).setR(v1);
							((ComplexFloat32Member) type).setI(v2);
						}
					};
		            
					break;
	
				case 6: // Unsigned16Bit
		        
					bytesPerElement = 2;
			        
					alg = (T) G.UINT16;
		            
					byteTransformer = new Procedure3<Boolean, byte[], U>() {
						@Override
						public void call(Boolean evenPixel, byte[] data, U type) {
							
							int v = decodeShort(data, 0, littleEndian) & 0xffff;
							
							((UnsignedInt16Member) type).setV(v);
						}
					};
		            
					break;
		            
		        case 12: // 16 bit floats
		        
					bytesPerElement = 2;
			        
		        	alg = (T) G.HLF;
		            
					byteTransformer = new Procedure3<Boolean, byte[], U>() {
						@Override
						public void call(Boolean evenPixel, byte[] data, U type) {
							
							short bits = decodeShort(data, 0, littleEndian);
							
							((Float16Member) type).setEncV(bits);
						}
					};
		            
		        	break;
		            
		        case 16: // RGB
		        
					bytesPerElement = 3;
			        
		        	alg = (T) G.RGB;
		            
					byteTransformer = new Procedure3<Boolean, byte[], U>() {
						@Override
						public void call(Boolean evenPixel, byte[] data, U type) {
							int r = data[0] & 0xff;
							int g = data[1] & 0xff;
							int b = data[2] & 0xff;
							
							((RgbMember) type).setR(r);
							((RgbMember) type).setG(g);
							((RgbMember) type).setB(b);
						}
					};
		            
		        	break;
		            
				case 101: // Unsigned4Bit:
			            
					bytesPerElement = 0.5;
			        
					alg = (T) G.UINT4;
						
					byteTransformer = new Procedure3<Boolean, byte[], U>() {
						@Override
						public void call(Boolean evenPixel, byte[] data, U type) {
								
							int v;
								
							if (evenPixel) {
									
								v = data[0] & 0x0f;
							}
							else {
									
								v = (data[0] & 0xf0) >> 4;
							}
								
							((SignedInt4Member) type).setV(v);
						}
					};
						
					break;
	
				default:
			        
			       	System.out.println("Unidentified MRC pixel type: "+dataType);
						
					return bundle;
			}
	
			U type = alg.construct();
	
			int nb = (int) bytesPerElement;
			
			if (nb < 1) nb = 1;  // 4 bit case
	
			byte[] byteBuffer = new byte[nb];

			axisOrder[0] = decodeInt(header, 64, littleEndian);
			axisOrder[1] = decodeInt(header, 68, littleEndian);
			axisOrder[2] = decodeInt(header, 72, littleEndian);

			if (axisOrder[0] < 1 || axisOrder[0] > 3) axisOrder[0] = 1;
			if (axisOrder[1] < 1 || axisOrder[1] > 3) axisOrder[1] = 2;
			if (axisOrder[2] < 1 || axisOrder[2] > 3) axisOrder[2] = 3;
			
			long cols = decodeInt(header, 0, littleEndian);
			long rows = decodeInt(header, 4, littleEndian);
			long sections = decodeInt(header, 8, littleEndian);
			
			int mx = decodeInt(header, 28, littleEndian);
			int my = decodeInt(header, 32, littleEndian);
			int mz = decodeInt(header, 36, littleEndian);
			
			float xlen = decodeFloat(header, 40, littleEndian);
			float ylen = decodeFloat(header, 44, littleEndian);
			float zlen = decodeFloat(header, 48, littleEndian);
            
			// newer files store origin here:
			
			float aOriginX = decodeFloat(header, 196, littleEndian);
			float aOriginY = decodeFloat(header, 200, littleEndian);
			float aOriginZ = decodeFloat(header, 204, littleEndian);
			
			// older files store origin here:
			
			float bOriginZ = decodeFloat(header, 208, littleEndian);
			float bOriginX = decodeFloat(header, 212, littleEndian);
			float bOriginY = decodeFloat(header, 216, littleEndian);
			
			// now choose between the two origin conventions
			
			if (header[208] == 'M' &&
				header[209] == 'A' &&
				header[210] == 'P' &&
				header[211] == ' ' )
			{
				originX = aOriginX;
				originY = aOriginY;
				originZ = aOriginZ;
			}
			else {

				originX = bOriginX;
				originY = bOriginY;
				originZ = bOriginZ;
			}

			if (swapOriginSign) {
			
				originX = -originX;
				originY = -originY;
				originZ = -originZ;
			}
			
			// and get cell spacing
			
			cellSpacingX = xlen / mx;
			cellSpacingY = ylen / my;
			cellSpacingZ = zlen / mz;
			
			if (cellSpacingX == 0) cellSpacingX = 1;
			if (cellSpacingY == 0) cellSpacingY = 1;
			if (cellSpacingZ == 0) cellSpacingZ = 1;
			
			if (axisOrder[0] == 1) {
				dims[0] = cols;
				origins[0] = BigDecimal.valueOf(originX);
				spacings[0] = BigDecimal.valueOf(cellSpacingX);
			}
			
			if (axisOrder[0] == 2) {
				dims[1] = cols;
				origins[1] = BigDecimal.valueOf(originX);
				spacings[1] = BigDecimal.valueOf(cellSpacingX);
			}
			
			if (axisOrder[0] == 3) {
				dims[2] = cols;
				origins[2] = BigDecimal.valueOf(originX);
				spacings[2] = BigDecimal.valueOf(cellSpacingX);
			}
			
			if (axisOrder[1] == 1) {
				dims[0] = rows;
				origins[0] = BigDecimal.valueOf(originY);
				spacings[0] = BigDecimal.valueOf(cellSpacingY);
			}

			if (axisOrder[1] == 2) {
				dims[1] = rows;
				origins[1] = BigDecimal.valueOf(originY);
				spacings[1] = BigDecimal.valueOf(cellSpacingY);
			}
			
			if (axisOrder[1] == 3) {
				dims[2] = rows;
				origins[2] = BigDecimal.valueOf(originY);
				spacings[2] = BigDecimal.valueOf(cellSpacingY);
			}
			
			if (axisOrder[2] == 1) {
				dims[0] = sections;
				origins[0] = BigDecimal.valueOf(originZ);
				spacings[0] = BigDecimal.valueOf(cellSpacingZ);
			}
			
			if (axisOrder[2] == 2) {
				dims[1] = sections;
				origins[1] = BigDecimal.valueOf(originZ);
				spacings[1] = BigDecimal.valueOf(cellSpacingZ);
			}
			
			if (axisOrder[2] == 3) {
				dims[2] = sections;
				origins[2] = BigDecimal.valueOf(originZ);
				spacings[2] = BigDecimal.valueOf(cellSpacingZ);
			}
			
			data = DimensionedStorage.allocate(type, dims);
	
			IntegerIndex idx = new IntegerIndex(3);
		
			for (long z = 0; z < sections; z++) {
				
				idx.set(axisOrder[2]-1, z);
				
				for (long y = 0; y < rows; y++) {
					
					idx.set(axisOrder[1]-1, y);
					
					for (long x = 0; x < cols; x++) {
						
						idx.set(axisOrder[0]-1, x);
						
						Boolean evenPixel = (x & 1) == 0;
						
						if (bytesPerElement < 1) {
							
							if (evenPixel)
								
								dis.read(byteBuffer, 0, 1);
							
							else // odd pixel
								
								;    // upper half pixel is already in the buffer 
						}
						else {
							
							for (int i = 0; i < byteBuffer.length; i++) {
								
								dis.read(byteBuffer, i, 1);
							}
						}

						byteTransformer.call(evenPixel, byteBuffer, type);
						
						data.set(idx, type);
					}
					
					// I don't think we need this
					//
					// skipEolBytesIfNeeded(dis, cols, bytesPerElement, bytesPerLine);
				}
			}
		} catch (IOException e) {
			
        	System.out.println(e.getMessage());
			
			return bundle;
		}
		
		LinearNdCoordinateSpace coordSpace =
				
				new LinearNdCoordinateSpace(spacings, origins);
		
		data.setCoordinateSpace(coordSpace);
		
		data.setValueType("intensity");
		data.setValueUnit("");
				
		data.setAxisType(axisOrder[0]-1, "X");
		data.setAxisUnit(axisOrder[0]-1, "dist");
		
		data.setAxisType(axisOrder[1]-1, "Y");
		data.setAxisUnit(axisOrder[1]-1, "dist");

		data.setAxisType(axisOrder[2]-1, "Z");
		data.setAxisUnit(axisOrder[2]-1, "dist");

		data.setName("MRC format file");
		
		data.setSource(fileURI.toString());
		
		// TODO: look in the headers and build any other relevant metadata.
		//   For instance there are all kinds of settings in the header
		//   and the extended header. Attach the metadata to the dataset
		//   here.
			
		int numLabels = decodeInt(header, 220, littleEndian);
        
		if (numLabels < 0) numLabels = 0;
		if (numLabels > 10) numLabels = 10;
        
		for (int l = 0; l < numLabels; l++) {
    
			StringBuilder builder = new StringBuilder();
			for (int b = 0; b < 80; b++) {
				builder.append((char) header[224 + 80*l + b]);
			}
		
			data.metadata().putString("Label "+l, builder.toString());
		}
		
		switch (dataType) {
        
			case 0: // bytes - signed or unsigned
				
				if (signedBytes)
	
					bundle.int8s.add((DimensionedDataSource<SignedInt8Member>) data);
	            
				else  // unsigned
			        
					bundle.uint8s.add((DimensionedDataSource<UnsignedInt8Member>) data);
					
				break;
	        
			case 1: // Signed16Bit
	        
				bundle.int16s.add((DimensionedDataSource<SignedInt16Member>) data);
	            
				break;
	            
			case 2: // Float32Bit
	        
				bundle.flts.add((DimensionedDataSource<Float32Member>) data);
	            
				break;
	            
			case 3: // Gaussian16Bit
		        
				bundle.gint16s.add((DimensionedDataSource<GaussianInt16Member>) data);
	            
				break;
	            
			case 4: // ComplexFloat32Bit:
	        
				bundle.cflts.add((DimensionedDataSource<ComplexFloat32Member>) data);
	            
				break;
	            
			case 6: // Unsigned16Bit
		        
				bundle.uint16s.add((DimensionedDataSource<UnsignedInt16Member>) data);
	            
				break;
	            
	        case 12: // Float16Bit:
		        
				bundle.hlfs.add((DimensionedDataSource<Float16Member>) data);
	            
	        	break;
	            
	        case 16: // RGB
	        
				bundle.rgbs.add((DimensionedDataSource<RgbMember>) data);
	            
	        	break;
	            
			case 101: // Unsigned4Bit
		        
				bundle.uint4s.add((DimensionedDataSource<UnsignedInt4Member>) data);
	            
				break;
	            
	        default:
	        
	        	break;
		}
		
		return bundle;
	}

	@SuppressWarnings("unused")
	private static
	
		void skipEolBytesIfNeeded(DataInputStream dis, long cols, double bytesPerElement, long bytesPerLine)
		
			throws IOException
	{
		double readSoFar = bytesPerElement * cols;
		
		long bytesSoFar = Math.round(readSoFar);
		
		for (long i = bytesSoFar; i < bytesPerLine; i++) {
			
			dis.readByte();
		}
		
	}
	
	private static
	
		short decodeShort(byte[] buffer, int offset, boolean littleEndian)
	{
		if (littleEndian) {

			return (short) (
					((buffer[offset+0] & 0xff) << 8) |
					((buffer[offset+1] & 0xff) << 0)
				);
		}
		else { // bigEndian

			return (short) (
					((buffer[offset+1] & 0xff) << 8) |
					((buffer[offset+0] & 0xff) << 0)
				);
		}
	}
	
	private static
	
		int decodeInt(byte[] buffer, int offset, boolean littleEndian)
	{
		if (littleEndian) {

			return (int) (
							((buffer[offset+0] & 0xff) << 24) |
							((buffer[offset+1] & 0xff) << 16) |
							((buffer[offset+2] & 0xff) << 8) |
							((buffer[offset+3] & 0xff) << 0)
						);
		}
		else {

			return (int) (
							((buffer[offset+3] & 0xff) << 24) |
							((buffer[offset+2] & 0xff) << 16) |
							((buffer[offset+1] & 0xff) << 8) |
							((buffer[offset+0] & 0xff) << 0)
						);
		}
	}
	
	private static
	
		float decodeFloat(byte[] buffer, int offset, boolean littleEndian)
	{
		int bits = decodeInt(buffer, offset, littleEndian);
		
		return Float.intBitsToFloat(bits);
	}
}
