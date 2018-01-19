package omar.wfs

import grails.gorm.transactions.Transactional
import groovy.json.JsonSlurper
import org.springframework.beans.factory.annotation.Value

import java.nio.charset.Charset

@Transactional(readOnly = true)

class OdsClientService
{
  @Value('${ods.api.metadatainfos.url}')
  def odsApiEndpoint

  def metadatainfosByField(Map<String,String> odsFields, String userToken)
  {
    def urlParams = odsFields.collect {
      "${it.key}=${URLEncoder.encode(it.value, Charset.defaultCharset().displayName())}"
    }.join('&')

    //TODO Implement OAuth integration with URL
    def url = "${odsApiEndpoint}/metadatainfos?${urlParams}".toURL()

    new JsonSlurper().parse(url)
  }
}
