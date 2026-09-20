package com.labflow.common.model;

/**
 * The kinds of biological sample the lab accepts. The constant names are the
 * lab's JSON wire values, so they must not be renamed.
 */
public enum SampleType {
    BLOOD,
    TISSUE,
    SWAB,
    URINE
}
