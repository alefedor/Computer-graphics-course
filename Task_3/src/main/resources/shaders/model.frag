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

uniform int biasType;
uniform int averageShadows;

float calculateShadow(vec4 fragPos, vec3 normal) {
    vec3 coords = fragPos.xyz / fragPos.w;
    coords = coords * 0.5 + 0.5;

    if (coords.z > 1.0)
        return 0.0f;

    float currentDepth = coords.z;
    float bias = 0.0f;
    if (biasType == 1)
        bias = 0.003f;
    if (biasType == 2)
        bias = max(0.03 * (1.0 - dot(normal, lightPosition)), 0.003);


    int neighbourhood = 5 * averageShadows;
    int neighbourhoodSize = (1 + 2 * neighbourhood) * (1 + 2 * neighbourhood);

    vec2 pixelSize = 1.0 / textureSize(depth_map, 0);
    float shadow = 0.0;

    for (int x = -neighbourhood; x <= neighbourhood; x++)
        for (int y = -neighbourhood; y <= neighbourhood; y++) {
            float closestDepth = texture(depth_map, coords.xy + vec2(x, y) * pixelSize * 0.2).r;
            shadow += currentDepth > closestDepth + bias ? (1.0 / neighbourhoodSize) : 0.0;
        }

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

    float shadow = calculateShadow(LightFragPos, normal);

    vec3 result = ambientColor + (1.0 - shadow) * (diffuseColor + specularColor);
    FragColor = vec4(result, 1.0f);
}