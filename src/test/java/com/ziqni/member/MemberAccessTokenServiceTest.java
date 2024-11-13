package com.ziqni.member;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MemberAccessTokenServiceTest {

    @Test
    void getToken() {

        final var token = MemberAccessTokenService.getToken( "your customer id", "your api key");

        assertTrue(!token.getErrors().isEmpty());
    }
}
