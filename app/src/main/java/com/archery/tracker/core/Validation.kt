package com.archery.tracker.core

data class ValidationError(val code: String, val message: String)

fun validateArrow(arrow: Arrow): List<ValidationError> {
    val errors = mutableListOf<ValidationError>()
    if (arrow.value !in VALID_ARROW_VALUES) {
        errors.add(ValidationError("ARROW_INVALID_VALUE", "${arrow.value} is not a scoring zone on an 80cm 6-ring face"))
    }
    if (arrow.isX && arrow.value != 10) {
        errors.add(ValidationError("ARROW_X_ON_NON_TEN", "X can only be recorded on a 10"))
    }
    return errors
}

fun validateRound(round: Round, sessionType: SessionType): List<ValidationError> {
    val errors = mutableListOf<ValidationError>()
    if (round.arrows.size > ARROWS_PER_ROUND) {
        errors.add(ValidationError("ROUND_TOO_MANY_ARROWS", "A round holds at most $ARROWS_PER_ROUND arrows"))
    }
    val maxIndex = ROUNDS_PER_SESSION.getValue(sessionType)
    if (round.index < 1 || round.index > maxIndex) {
        errors.add(ValidationError("ROUND_INDEX_OUT_OF_RANGE", "A $sessionType session holds rounds 1 to $maxIndex"))
    }
    round.arrows.forEach { errors.addAll(validateArrow(it)) }
    return errors
}

fun validateSession(session: Session, rounds: List<Round>): List<ValidationError> {
    val errors = mutableListOf<ValidationError>()
    if (rounds.isEmpty()) {
        errors.add(ValidationError("SESSION_NO_ROUNDS", "A session must contain at least one started round"))
    }
    val limit = ROUNDS_PER_SESSION.getValue(session.type)
    if (rounds.size > limit) {
        errors.add(ValidationError("SESSION_ROUND_COUNT", "A ${session.type} session holds at most $limit rounds"))
    }
    val indexes = rounds.map { it.index }
    if (indexes.toSet().size != indexes.size) {
        errors.add(ValidationError("SESSION_DUPLICATE_ROUND_INDEX", "Round indexes within a session must be unique"))
    }
    rounds.forEach { errors.addAll(validateRound(it, session.type)) }
    return errors
}
