#version 330 core

const int CASCADES = 4;
const int TOTAL = CASCADES + 1;

layout (location = 0) out vec4 FragColor;

in vec3 FragPos;
in vec3 Normal;
in vec2 TexCoords;
in vec4 LightFragPos[TOTAL];

uniform sampler2D texture_diffuse;
uniform sampler2D texture_specular;
uniform sampler2D depth_map0;
uniform sampler2D depth_map1;
uniform sampler2D depth_map2;
uniform sampler2D depth_map3;
uniform sampler2D depth_map4;

uniform float cascade_end0;
uniform float cascade_end1;
uniform float cascade_end2;
uniform float cascade_end3;


uniform vec3 ambient;
uniform vec3 diffuse;
uniform vec3 specular;

uniform vec3 cameraPosition;
uniform vec3 lightPosition;

uniform int useCascades;

float get_texture(int cascade, vec2 v) {
    float ans = 0.0f;
    if (cascade == 0)
        ans = texture(depth_map0, v).r;
    if (cascade == 1)
        ans = texture(depth_map1, v).r;
    if (cascade == 2)
        ans = texture(depth_map2, v).r;
    if (cascade == 3)
        ans = texture(depth_map3, v).r;
    if (cascade == 4)
        ans = texture(depth_map4, v).r;
    return ans;
}

float calculateShadow(vec3 coords, int cascade) {
    float currentDepth = coords.z;

    int neighbourhood = 0;
    int neighbourhoodSize = (1 + 2 * neighbourhood) * (1 + 2 * neighbourhood);

    vec2 pixelSize = 1.0 / textureSize(depth_map0, 0);
    float shadow = 0.0;

    for (int x = -neighbourhood; x <= neighbourhood; x++)
        for (int y = -neighbourhood; y <= neighbourhood; y++) {
            float closestDepth = get_texture(cascade, coords.xy + vec2(x, y) * pixelSize * 0.2);
            shadow += currentDepth > closestDepth ? (1.0 / neighbourhoodSize) : 0.0;
        }

    return shadow;
}

float calculateShadow(vec4 FragPos, int cascade) {
    vec3 coords = FragPos.xyz / FragPos.w;
    coords = coords * 0.5 + 0.5;

    if (coords.z > 1.0) return 0.0f;

    return calculateShadow(coords, cascade);
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
    float shadow = 0;
    if (useCascades == 0) {
        shadow = calculateShadow(LightFragPos[CASCADES], CASCADES);
    } else {
        for (int i = 0; i < CASCADES; i++) {
            vec3 coords = LightFragPos[i].xyz / LightFragPos[i].w;
            coords = coords * 0.5 + 0.5;

            if (coords.z > 1.0 - 1e-2 || coords.x > 1.0 - 1e-2 || coords.y > 1.0 - 1e-2 || coords.x < 0.0 + 1e-2 || coords.y < 0.0 + 1e-2) continue;

            shadow = calculateShadow(coords, i);
            break;
        }
    }

    vec3 result = ambientColor + (1.0 - shadow) * (diffuseColor + specularColor);
    FragColor = vec4(result, 1.0f);
}