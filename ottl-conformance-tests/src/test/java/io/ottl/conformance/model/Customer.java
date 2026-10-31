/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.conformance.model;

/** Mutable JavaBean/POJO used to validate MUTATE semantics. */
public class Customer {

    private String name;
    private boolean premium;
    private String tier;

    public Customer() {
    }

    public Customer(String name, boolean premium, String tier) {
        this.name = name;
        this.premium = premium;
        this.tier = tier;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isPremium() {
        return premium;
    }

    public void setPremium(boolean premium) {
        this.premium = premium;
    }

    public String getTier() {
        return tier;
    }

    public void setTier(String tier) {
        this.tier = tier;
    }
}
