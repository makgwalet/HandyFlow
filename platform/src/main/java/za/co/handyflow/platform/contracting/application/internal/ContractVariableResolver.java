package za.co.handyflow.platform.contracting.application.internal;

import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.util.Map;

@Component
public class ContractVariableResolver {

    public String resolve(String template, Map<String, String> variables) {
        if (template == null) return "";
        String result = template;

        // Always substitute date with today
        result = result.replace("{{date}}", LocalDate.now().toString());

        // Substitute any provided variables
        if (variables != null) {
            for (Map.Entry<String, String> entry : variables.entrySet()) {
                result = result.replace("{{" + entry.getKey() + "}}",
                        entry.getValue() != null ? entry.getValue() : "");
            }
        }
        return result;
    }
}