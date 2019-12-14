#version 330 core

layout (location = 0) out vec4 FragColor;

in vec3 FragPos;
in vec3 Normal;
in vec2 TexCoords;

uniform sampler2D texture_diffuse;
uniform sampler2D texture_specular;

uniform vec3 ambient;
uniform vec3 diffuse;
uniform vec3 specular;

uniform vec3 cameraPosition;
uniform vec3 lightPosition;

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

    vec3 result = ambientColor + diffuseColor + specularColor;
    FragColor = vec4(result, 1.0f);
}