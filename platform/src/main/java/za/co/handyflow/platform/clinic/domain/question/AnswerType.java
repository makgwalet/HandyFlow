package za.co.handyflow.platform.clinic.domain.question;

/** How a question is answered. The stored value shape for each type is documented on {@link AnswerValidator}. */
public enum AnswerType {
    YES_NO, SINGLE_SELECT, MULTI_SELECT, RADIO_GROUP, CHECKLIST, TOGGLE,
    NUMBER, DECIMAL, TEXT, LONG_TEXT, DATE, DATE_TIME, DURATION, MEASUREMENT, SCALE, BODY
}
