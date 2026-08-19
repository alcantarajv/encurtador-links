# Imagem da aplicacao, em dois estagios.
#
# Constroi com:  docker build -t encurtador-links .
# Roda com:      docker compose --profile app up -d
#
# A ideia dos dois estagios: o primeiro precisa do JDK inteiro, do Maven e de
# todo o codigo-fonte para compilar; o segundo so precisa da JRE e do resultado.
# Como a imagem final parte do zero e copia apenas o que interessa do primeiro
# estagio, nada disso -- fonte, Maven, cache do .m2 -- vai parar no que e
# publicado.
#
# Medido neste projeto: a mesma aplicacao empacotada num estagio unico da 1,01 GB
# em disco (389 MB para trafegar no registro). Em dois estagios, sao 412 MB em
# disco e 134 MB para trafegar.


# ===========================================================================
# Estagio 1 -- compilacao
# ===========================================================================
FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /build

# O Maven Wrapper deste projeto esta em modo "only-script" (ver
# .mvn/wrapper/maven-wrapper.properties): nao ha jar do wrapper no repositorio,
# o script baixa o Maven na primeira execucao. A imagem alpine e enxuta demais
# para isso -- nao traz curl nem unzip.
RUN apk add --no-cache curl unzip

# --- Por que copiar o pom.xml sozinho antes do codigo ---------------------
#
# Cada instrucao do Dockerfile vira uma camada, e o Docker so refaz uma camada
# se alguma das anteriores mudou. Copiando o pom.xml primeiro e baixando as
# dependencias em seguida, essa camada so e refeita quando o pom.xml muda. Se
# o codigo e o pom fossem copiados juntos, mexer numa linha de Java invalidaria
# o download e as dependencias seriam baixadas de novo a cada build.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -ntp dependency:go-offline

# So agora o codigo, que muda a cada commit.
COPY src/ src/

# Os testes NAO rodam aqui de proposito. A partir da Etapa 7 os testes de
# integracao sobem PostgreSQL e Redis via Testcontainers, o que exige acesso a
# um daemon do Docker -- que nao existe dentro do container de build. Rodar a
# suite e responsabilidade da maquina do desenvolvedor e da integracao continua
# (Etapa 9), onde o daemon esta disponivel.
RUN ./mvnw -B -ntp package -DskipTests

# --- Por que quebrar o jar em duas partes ---------------------------------
#
# O "jar gordo" do Spring Boot tem 66 MB, e 64 MB disso sao bibliotecas de
# terceiros que so mudam quando o pom.xml muda. Se ele fosse copiado inteiro
# para a imagem final, cada alteracao de uma linha de Java geraria uma camada
# nova de 66 MB para reconstruir, armazenar e enviar ao registro.
#
# O comando abaixo separa o conteudo em:
#   extracted/lib/      -> as 102 dependencias, 64 MB, mudam raramente
#   extracted/app.jar   -> so o codigo deste projeto, 64 KB, muda sempre
#
# ARMADILHA DE VERSAO: praticamente todo tutorial de Dockerfile para Spring
# Boot usa "java -Djarmode=layertools -jar app.jar extract". Esse modo foi
# substituido no Boot 3.3 e REMOVIDO no Boot 4 -- ele responde
# "Unsupported jarmode 'layertools'". O substituto e o jarmode "tools".
RUN java -Djarmode=tools -jar target/encurtador-links-*.jar extract \
        --application-filename app.jar \
        --destination extracted


# ===========================================================================
# Estagio 2 -- execucao
# ===========================================================================
FROM eclipse-temurin:21-jre-alpine AS runtime

# Container roda como root por padrao. Se alguem escapar da aplicacao, escapa
# como root -- e o usuario dentro do container e o mesmo do host em algumas
# configuracoes de volume. A aplicacao nao precisa de privilegio nenhum: nao
# escreve em disco, nao abre porta baixa (8080 > 1024).
RUN addgroup -S encurtador && adduser -S -G encurtador encurtador

WORKDIR /app

# A ordem aqui importa, e e o motivo de toda a separacao feita acima: as
# bibliotecas entram numa camada propria, ANTES do codigo. Ao reconstruir a
# imagem depois de mexer no Java, o Docker reaproveita os 64 MB de lib/ e so
# refaz os 64 KB do app.jar.
COPY --from=build --chown=encurtador:encurtador /build/extracted/lib/ lib/
COPY --from=build --chown=encurtador:encurtador /build/extracted/app.jar app.jar

USER encurtador

# Documenta a porta para quem le e para o "docker run -P". Nao publica nada
# sozinho: quem publica e o "ports:" do compose ou o -p do docker run.
EXPOSE 8080

# --- Sobre o ENTRYPOINT ---------------------------------------------------
#
# Forma "exec" (lista JSON), nao forma shell. Na forma shell o processo 1 do
# container seria o /bin/sh e a JVM ficaria pendurada nele como filha; o
# SIGTERM do "docker stop" chegaria no shell e nunca na JVM, que so morreria no
# SIGKILL dez segundos depois. Como PID 1, a JVM recebe o sinal e o Spring
# executa o desligamento gracioso configurado em application.properties.
#
# -XX:MaxRAMPercentage: sem isso a JVM reserva no maximo 1/4 da memoria do
# container para o heap -- num limite de 512 MB, so 128 MB, e o resto fica
# ocioso enquanto a aplicacao sofre com GC.
#
# Para acrescentar flags sem reconstruir a imagem, use a variavel de ambiente
# JAVA_TOOL_OPTIONS: a propria JVM a le na inicializacao.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
