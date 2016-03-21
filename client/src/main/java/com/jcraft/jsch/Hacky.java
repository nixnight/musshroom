package com.jcraft.jsch;

public class Hacky {
	public static void hacky(Session session, String username) {
		session.setUserName(username);
	}
}
