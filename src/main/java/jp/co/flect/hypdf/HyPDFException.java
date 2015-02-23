package jp.co.flect.hypdf;

import java.io.IOException;

public class HyPDFException extends IOException {
	private static final long serialVersionUID = 7147409449480789398L;
	private int statusCode;
	
	public HyPDFException(int statusCode, String msg) {
		super(msg);
		this.statusCode = statusCode;
	}

	public HyPDFException(Throwable t) {
		super(t);
	}

	public int getStatusCode() { return this.statusCode;}
}