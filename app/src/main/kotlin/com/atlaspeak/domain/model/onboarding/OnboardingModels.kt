package com.atlaspeak.domain.model.onboarding

enum class OnboardingStep {
    Welcome,
    Google,
    Password,
    Profile,
    Notifications,
    HealthConnect,
    Location,
    Biometrics,
    Done,
}

enum class PasswordStrength {
    Weak,
    Medium,
    Strong,
}
