package com.wuntee.ct;

public class EncryptionSpec {
	private String secretKeySpecType;
	private String cipherType;
	private byte[] key;
	private byte[] iv;
	private int encryptOrDecrypt;
	
	public String getSecretKeySpecType() {
		return secretKeySpecType;
	}
	public void setSecretKeySpecType(String secretKeySpecType) {
		this.secretKeySpecType = secretKeySpecType;
	}
	public String getCipherType() {
		return cipherType;
	}
	public void setCipherType(String cipherType) {
		this.cipherType = cipherType;
	}
	public byte[] getKey() {
		return key;
	}
	public void setKey(byte[] key) {
		this.key = key;
	}
	public byte[] getIv() {
		return iv;
	}
	public void setIv(byte[] iv) {
		this.iv = iv;
	}
	public int getEncryptOrDecrypt() {
		return encryptOrDecrypt;
	}
	public void setEncryptOrDecrypt(int encryptOrDecrypt) {
		this.encryptOrDecrypt = encryptOrDecrypt;
	}
	
}
