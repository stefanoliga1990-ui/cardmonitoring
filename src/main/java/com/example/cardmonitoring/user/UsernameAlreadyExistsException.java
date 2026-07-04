package com.example.cardmonitoring.user;

public class UsernameAlreadyExistsException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public UsernameAlreadyExistsException() {
		super("Username is already in use");
	}
}
