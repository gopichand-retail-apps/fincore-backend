package com.portfolio.fincore.domain;

import java.io.Serializable;

public record DemoPrincipal(String userId, String displayName, DemoRole role) implements Serializable {
}