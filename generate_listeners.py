import re

file_path = 'ss7-core/src/main/java/com/company/ss7ha/core/listeners/GenericMapServiceListener.java'

with open(file_path, 'r') as f:
    content = f.read()

# Pattern to find void methods with {} empty body
# Example: public void onUpdateLocationRequest(UpdateLocationRequest request) {}
pattern = r'public void (on([a-zA-Z0-9_]+))\(([^)]+)\)\s*\{\}'

def replacement(match):
    method_name = match.group(1)
    event_name = match.group(2) # e.g., UpdateLocationRequest
    params = match.group(3) # e.g., UpdateLocationRequest request
    
    # Extract parameter name
    param_parts = params.split()
    param_name = param_parts[-1]
    
    # Convert CamelCase to SNAKE_CASE for event type
    # e.g. UpdateLocationRequest -> UPDATE_LOCATION_REQUEST
    s1 = re.sub('(.)([A-Z][a-z]+)', r'\1_\2', event_name)
    snake_case = re.sub('([a-z0-9])([A-Z])', r'\1_\2', s1).upper()
    
    # Check if param has getMAPDialog() method (usually Requests/Responses in JSS7 do)
    # If not sure, we pass null as dialog, or try to cast.
    # Safe approach: pass param_name and let helper extract dialog if possible or overload helper.
    
    # For generated code, we assume payload has getMAPDialog() if it is a MAP message.
    # Most JSS7 messages interface don't strictly have getMAPDialog() in common interface except via casting.
    # We will update helper to accept Object and check instance.
    
    return f'''public void {method_name}({params}) {{
        publishMapEvent("{snake_case}", {param_name});
    }}'''

new_content = re.sub(pattern, replacement, content)

# Update helper method to extract dialog dynamically
helper_method = r'private void publishMapEvent\(String type, Object payload, MAPDialog dialog\) \{'
new_helper = '''private void publishMapEvent(String type, Object payload) {
        MAPDialog dialog = null;
        try {
            if (payload instanceof org.restcomm.protocols.ss7.map.api.MAPMessage) {
                dialog = ((org.restcomm.protocols.ss7.map.api.MAPMessage) payload).getMAPDialog();
            }
        } catch (Exception e) {
            // Ignore if dialog extraction fails
        }
        publishMapEvent(type, payload, dialog);
    }

    private void publishMapEvent(String type, Object payload, MAPDialog dialog) {'''

new_content = new_content.replace('private void publishMapEvent(String type, Object payload, MAPDialog dialog) {', new_helper)

# Remove the manually added specific methods to avoid duplication if regex catches them or not
# Actually the regex only catches empty methods {}, so my manual changes (which have body) won't be touched.
# But I want to overwrite them to use the generic naming convention I just defined.
# So I will first revert the file to "all empty" state via tool or just let regex run on remaining empty ones.

with open(file_path, 'w') as f:
    f.write(new_content)
