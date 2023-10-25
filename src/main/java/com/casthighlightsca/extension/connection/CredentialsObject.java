package com.casthighlightsca.extension.connection;

public class CredentialsObject {
    private String url;
    private String token;
    private int expiration;
    private String companyId;

    public CredentialsObject(String url, String token, int expiration, String companyId)  {
        super();
        this.url = url;
        this.token = token;
        this.expiration = expiration;
        this.companyId = companyId;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public int getExpiration() {
        return expiration;
    }

    public void setExpiration(int expiration) {
        this.expiration = expiration;
    }

    public String getCompanyId() {
        return companyId;
    }

    public void setCompanyId(String companyId) {
        this.companyId = companyId;
    }


}