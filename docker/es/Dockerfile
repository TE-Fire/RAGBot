# Elasticsearch + IK 中文分词
# IK 插件版本必须与 ES 版本严格一致，否则插件加载失败
ARG ES_VERSION=9.4.2
FROM docker.elastic.co/elasticsearch/elasticsearch:${ES_VERSION}

ARG ES_VERSION
# infini 插件源：官方推荐的离线包下载地址
RUN bin/elasticsearch-plugin install --batch \
      "https://get.infini.cloud/elasticsearch/analysis-ik/${ES_VERSION}"
