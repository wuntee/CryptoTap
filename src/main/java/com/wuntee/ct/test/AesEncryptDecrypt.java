package com.wuntee.ct.test;

import java.net.URL;
import java.net.URLClassLoader;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class AesEncryptDecrypt {

	/**
	 * @param args
	 * @throws NoSuchPaddingException 
	 * @throws NoSuchAlgorithmException 
	 * @throws BadPaddingException 
	 * @throws IllegalBlockSizeException 
	 * @throws InvalidAlgorithmParameterException 
	 * @throws InvalidKeyException 
	 * @throws InterruptedException 
	 */
	public static void main(String[] args) throws NoSuchAlgorithmException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException, InvalidKeyException, InvalidAlgorithmParameterException, InterruptedException {
        /*
		ClassLoader cl = ClassLoader.getSystemClassLoader();   
        URL[] urls = ((URLClassLoader)cl).getURLs();
        for(URL url: urls){
        	System.out.println(url.getFile());
        }
        */

		byte [] key = new byte[]{-65, -45, -15, -41, -68, -33, -124, -44, 106, -115, 92, 107, -118, 85, 97, 35};
		byte [] cipherText = new byte[]{-47, 55, 32, 84, -114, 15, -71, 4, -75, 2, 119, 126, 85, 96, 70, -43, 36, 25, 51, -49, 29, -54, -104, -79, -17, -92, 2, -125, -127, 15, -43, -76};
		
		String plaintext = "this is another string that will be encrypted";
		SecretKeySpec keyspec = new SecretKeySpec(key, "AES");
	    Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
	    byte[] ivbyte ={0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00};
	    IvParameterSpec iv = new IvParameterSpec(ivbyte);
	    cipher.init(Cipher.DECRYPT_MODE, keyspec, iv);
	    byte[] decryptedText = cipher.doFinal(cipherText);
	    cipher.init(Cipher.ENCRYPT_MODE, keyspec, iv);
	    byte [] encryptedText = cipher.doFinal(plaintext.getBytes());
	}
	
	public static void testMethod(){
		System.out.println("This is a test method!");
	}
}
