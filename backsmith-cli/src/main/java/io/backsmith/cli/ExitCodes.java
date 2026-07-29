package io.backsmith.cli;

public final class ExitCodes {
    public static final int SUCCESS = 0;
    public static final int GENERAL_ERROR = 1;
    public static final int INVALID_ARGUMENTS = 2;
    public static final int INVALID_CONFIGURATION = 3;
    public static final int CONFLICTS = 4;
    public static final int GENERATION_FAILED = 5;
    public static final int VALIDATION_FAILED = 6;
    public static final int ENVIRONMENT_PROBLEM = 7;
    private ExitCodes() {}
}
