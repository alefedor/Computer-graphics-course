#version 330 core

layout (location = 0) out vec4 FragColor;

in vec3 FragPos;
in vec3 Normal;
in vec2 TexCoords;
in vec4 LightFragPos;

uniform sampler2D texture_diffuse;
uniform sampler2D texture_specular;
uniform sampler2D depth_map;


uniform vec3 ambient;
uniform vec3 diffuse;
uniform vec3 specular;

uniform vec3 cameraPosition;
uniform vec3 lightPosition;


float calculateShadow(vec4 fragPos) {
    vec3 coords = fragPos.xyz / fragPos.w;
    coords = coords * 0.5 + 0.5;
    float closestDepth = texture(depth_map, coords.xy).r;
    float currentDepth = coords.z;
    float shadow = currentDepth > closestDepth + 0.001 ? 1.0 : 0.0;
    return shadow;
}

void main()
{
    vec3 texture_diffuse_color = texture(texture_diffuse, TexCoords).rgb;
    vec3 ambientColor = ambient * texture_diffuse_color;

    vec3 normal = normalize(Normal);
    vec3 lightDirection = normalize(lightPosition);
    float diff = max(dot(normal, lightDirection), 0.0f);
    vec3 diffuseColor = diffuse * diff * texture_diffuse_color;

    vec3 viewDirection = normalize(cameraPosition - FragPos);
    vec3 reflectDirection = reflect(-lightDirection, normal);
    float spec = max(dot(viewDirection, reflectDirection), 0.0f);
    float initial_spec = spec;
    for (int i = 0; i < 3; i++)
        spec = spec * spec;
    spec = spec * initial_spec;
    vec3 specularColor = specular * spec * texture(texture_specular, TexCoords).rgb;

    float shadow = calculateShadow(LightFragPos);

    vec3 result = ambientColor + (1.0 - shadow) * (diffuseColor + specularColor);
    FragColor = vec4(result, 1.0f);
}