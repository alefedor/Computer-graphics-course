#version 330 core

const int CASCADES = 4;
const int TOTAL = CASCADES + 1;

layout (location = 0) in vec3 aPos;
layout (location = 1) in vec3 aNormal;
layout (location = 4) in vec2 aTexCoords;

uniform mat4 model;
uniform mat4 view;
uniform mat4 projection;

uniform mat4 lightProjection;
uniform mat4 lightProjection0;
uniform mat4 lightProjection1;
uniform mat4 lightProjection2;
uniform mat4 lightProjection3;
uniform mat4 lightProjection4;

uniform mat4 lightView;

out vec3 FragPos;
out vec3 Normal;
out vec2 TexCoords;
out vec4 LightFragPos[TOTAL];

void main()
{
    FragPos = vec3(model * vec4(aPos, 1.0f));
    LightFragPos[0] = lightProjection0 * (lightView * vec4(FragPos, 1.0));
    LightFragPos[1] = lightProjection1 * (lightView * vec4(FragPos, 1.0));
    LightFragPos[2] = lightProjection2 * (lightView * vec4(FragPos, 1.0));
    LightFragPos[3] = lightProjection3 * (lightView * vec4(FragPos, 1.0));
    LightFragPos[4] = lightProjection4 * (lightView * vec4(FragPos, 1.0));
    Normal = mat3(transpose(inverse(model))) * aNormal;
    TexCoords = aTexCoords;
    gl_Position = projection * (view * (model * vec4(aPos, 1.0f)));
}