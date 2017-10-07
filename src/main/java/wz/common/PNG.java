/*
    This file is part of Lachelein: MapleStory Web Database
    Copyright (C) 2017  Brenterino <therealspookster@gmail.com>

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/
package wz.common;

import java.awt.Image;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.PixelInterleavedSampleModel;
import java.awt.image.Raster;
import java.awt.image.SampleModel;
import java.awt.image.WritableRaster;
import java.util.zip.Inflater;

/**
 *
 * @author Brenterino
 */
public final class PNG {

    private int width;
    private int height;
    private int format;
    private byte[] data;
    private Image img = null;
    private boolean inflated = false;
    private static final int[] ZAHLEN = new int[]{0x02, 0x01, 0x00, 0x03};

    public PNG(int w, int h, int f, byte[] rD) {
        width = w;
        height = h;
        format = f;
        data = rD;
    }

    public Image getImage(boolean store) {
        if (img != null) {
            return img;
        }
        if (!inflated) {
            inflateData();
        }
        Image ret = createImage();
        if (store) {
            img = ret;
        }
        return ret;
    }

    public void inflateData() {
        int len, size;
        
        byte[] unc = new byte[height * width * 8];
        Inflater dec = new Inflater(true);
        dec.setInput(data, 0, data.length);
        try {
        	len = dec.inflate(unc);
        } catch (Exception e) {
        	e.printStackTrace();
            unc = data;
            len = unc.length;
        }
        dec.end();
        
        size = len;
        switch (format) {
            case 2:
                size *= 2;
            case 1:
            case 513:
                size *= 2;
                break;
            case 517:
                size /= 128;
                break;
            case 1026:
                // DXT1 Format
                System.out.println("DXT3 Format is currently unsupported.");
                break;
            case 2050:
            	// DXT5
                System.out.println("DXT5 Format is currently unsupported.");
                System.out.println("Size = " + size + " Len = " + data.length);
            	break;
            default:
                System.out.println("New image format: " + format);
                break;
        }
        
        int index;
        byte[] decBuff = new byte[size];
        switch (format) {
            case 1:
                for (int i = 0; i < len; i++) {
                    int lo = unc[i] & 0x0F;
                    int hi = unc[i] & 0xF0;
                    index = i << 1;
                    decBuff[index] = (byte) (((lo << 4) | lo) & 0xFF);
                    decBuff[index + 1] = (byte) (hi | (hi >>> 4) & 0x0F);
                }
                break;
            case 2:
                decBuff = unc;
                break;
            case 513:
                for (int i = 0; i < len; i += 2) {
                    int r = (unc[i + 1]) & 0xF8;
                    int g = ((unc[i + 1] & 0x07) << 5) | ((unc[i] & 0xE0) >> 3);
                    int b = ((unc[i] * 0x1F) << 3);
                    index = i << 1;
                    decBuff[index] = (byte) (b | (b >> 5));
                    decBuff[index + 1] = (byte) (g | (g >> 6));
                    decBuff[index + 2] = (byte) (r | (r >> 5));
                    decBuff[index + 3] = (byte) 0xFF;
                }
                break;
            case 517:
                int a;
                for (int i = 0; i < len; i++) {
                    for (int j = 0; j < 8; j++) {
                        a = ((unc[i] & (0x01 << (7 - j))) >> (7 - j)) * 0xFF;
                        for (int k = 0; k < 16; k++) {
                            index = (i << 9) + (j << 6) + k * 2;
                            decBuff[index] = (byte) a;
                            decBuff[index + 1] = (byte) a;
                            decBuff[index + 2] = (byte) a;
                            decBuff[index + 3] = (byte) 0xFF;
                        }
                    }
                }
                break;
            case 2050:
            	Color[] colorTable = new Color[4];
            	int[] colorIdxTable = new int[16];
            	int[] alphaTable   = new int[8];
            	int[] alphaIdxTable = new int[16];
            	
            	for (int y = 0; y < height; y += 4) {
            		for (int x = 0; x < width; x += 4) {
            			
            			int offset = x * 4 + y * width;
            			
            			// ExpandAlphaTableDXT5(alphaTable, rawData[off + 0], rawData[off + 1]);
            			expandAlphaTableDXT5(alphaTable, unc[offset] & 0xFF, unc[offset + 1] & 0xFF);
            			
            			// ExpandAlphaIndexTableDXT5(alphaIdxTable, rawData, off + 2);
            			expandAlphaIndexTableDXT5(alphaIdxTable, unc, offset + 2);
            			
            			// may be reverse endian-ness
            			int u0 = 0xFFFF & ((unc[offset +  8]) | (unc[offset +  9] << 8));
            			int u1 = 0xFFFF & ((unc[offset + 10]) | (unc[offset + 11] << 8));

        			    // ExpandColorTable(colorTable, u0, u1);
            			expandColorTable(colorTable, u0, u1);
            			
            			// ExpandColorIndexTable(colorIdxTable, rawData, off + 12);
            			expandColorIndexTable(colorIdxTable, unc, offset + 12);
            			
            			for (int j = 0; j < 4; j++) {
            				for (int i = 0; i < 4; i++) {
            					setPixel(decBuff,
            							x + i,
            							y + j,
            							width,
            							colorTable[colorIdxTable[j * 4 + i]],
            							alphaTable[alphaIdxTable[(j * 4 + i) % 4]]); // hack alpha channel
            				}
            			}
            			
            		}
            	}
            	break;
        }
        data = decBuff;
    }
    
    private void setPixel(byte[] data, int x, int y, int width, Color color, int alpha) {
    	int offset = (y * width + x) * 4;

    	data[offset + 2] = (byte) color.R;    //R
    	data[offset + 1] = (byte) color.G;    //G
    	data[offset + 0] = (byte) color.B;    //B
    	data[offset + 3] = (byte) alpha;      //A
    	
    }
    
    private void expandAlphaTableDXT5(int[] alphaTable, int a0, int a1) {
		alphaTable[0] = a0;
		alphaTable[1] = a1;
		
		if (a0 > a1) {
			for (int i = 2; i < 8; i++) {
				alphaTable[i] = (((8 - i) * a0 + (i - 1) * a1) / 7);
			}
		} else {
			for (int i = 2; i < 6; i++) {
				alphaTable[i] = (((6 - i) * a0 + (i - 1) * a1) / 5);
            }
			alphaTable[6] = 0;
			alphaTable[7] = 255;
		}
		
		/*for (int i = 0; i < 8; i++) {
			System.out.print(alphaTable[i]);
			if (i != 7) System.out.print(", ");
		}
		System.out.println();*/
    }
    
    private void expandAlphaIndexTableDXT5(int[] alphaIdxTable, byte[] unc, int tOffset) {
		for (int i = 0; i < 16; i += 8, tOffset += 3) {
			int flags = unc[tOffset] |
					    unc[tOffset + 1] << 8 |
					    unc[tOffset + 2] << 16;
			
			for (int j = 0; j < 8; j++) {
				int mask = 0x07 << (3 * j);
				
				alphaIdxTable[i + j] = (flags & mask) >>> (3 * j);
			}
		}
    }
    
    private void expandColorTable(Color[] colorTable, int u0, int u1) {
		colorTable[0] = rgb565ToColor(u0); // RGB565ToColor(c0)
		colorTable[1] = rgb565ToColor(u1); // RGB565ToColor(c1)
		
		if (u0 > u1) {
			colorTable[2] = new Color(
					(colorTable[0].R * 2 + colorTable[1].R) / 3, 
					(colorTable[0].G * 2 + colorTable[1].G) / 3, 
					(colorTable[0].B * 2 + colorTable[1].B) / 3
			);
			colorTable[3] = new Color(
					(colorTable[0].R + colorTable[1].R * 2) / 3, 
					(colorTable[0].G + colorTable[1].G * 2) / 3, 
					(colorTable[0].B + colorTable[1].B * 2) / 3
			);
 		} else {
			colorTable[2] = new Color(
					(colorTable[0].R + colorTable[1].R) / 2, 
					(colorTable[0].G + colorTable[1].G) / 2, 
					(colorTable[0].B + colorTable[1].B) / 2);
            colorTable[3] = new Color(0, 0, 0);
		}
		
    }
    
    private void expandColorIndexTable(int[] colorIdxTable, byte[] unc, int tOffset) {
		for (int i = 0; i < 16; i += 4, tOffset++) {
			colorIdxTable[i    ] =  unc[tOffset] & 0x03;
			colorIdxTable[i + 1] = (unc[tOffset] & 0x0C) >>> 2;
			colorIdxTable[i + 2] = (unc[tOffset] & 0x30) >>> 4;
			colorIdxTable[i + 3] = (unc[tOffset] & 0xC0) >>> 6;
		}
    }
    
    private static Color rgb565ToColor(int val) {
    	int r = (val >>> 11) & 0x1F;
    	int g = (val >>>  5) & 0x3F;
    	int b = val & 0x1F;

        return new Color(
        		(r << 3) | (r >>> 2),
        		(g << 2) | (g >>> 4),
        		(b << 3) | (b >>> 2)
        );
    }
    
    private static class Color {
    	
    	public int R;
    	public int G;
    	public int B;
    	
    	public Color(int r, int g, int b) {
    		this.R = r & 0xFF;
    		this.G = g & 0xFF;
    		this.B = b & 0xFF;
    	}
    }

    private Image createImage() {
        DataBufferByte imgData = new DataBufferByte(data, data.length);
        SampleModel model = new PixelInterleavedSampleModel(DataBuffer.TYPE_BYTE, width, height, 4, width * 4, ZAHLEN);
        WritableRaster raster = Raster.createWritableRaster(model, imgData, new Point(0, 0));
        BufferedImage ret = new BufferedImage(width, height, BufferedImage.TYPE_4BYTE_ABGR);
        ret.setData(raster);
        return ret;
    }

    public boolean isInflated() {
        return inflated;
    }

    public byte[] rawData() {
        return data;
    }
    
    public int getFormat() {
        return format;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 97 * hash + this.width;
        hash = 97 * hash + this.height;
        return hash;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof PNG) {
            PNG other = (PNG) o;
            return other.height == height && other.width == width
                    && other.data == data;
        }
        return false;
    }
}
