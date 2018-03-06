package omar.wfs

import grails.transaction.Transactional
import groovy.json.JsonBuilder
import groovy.xml.StreamingMarkupBuilder
import groovy.json.StreamingJsonBuilder
import groovy.json.JsonSlurper

@Transactional(readOnly=true)
class WebFeatureService
{
    def grailsLinkGenerator
    def geoscriptService
    def odsClientService

    static final def ogcNamespaces = [
      wfs: 'http://www.opengis.net/wfs',
      gml: 'http://www.opengis.net/gml',
      ogc: 'http://www.opengis.net/ogc',
      ows: 'http://www.opengis.net/ows',
      xs: 'http://www.w3.org/2001/XMLSchema',
      xlink: 'http://www.w3.org/1999/xlink',
      xsi: 'http://www.w3.org/2001/XMLSchema-instance'
    ]

    static final def outputFormats = [
      'application/gml+xml; version=3.2',
      'application/json',
      'application/vnd.google-earth.kml xml',
      'application/vnd.google-earth.kml+xml',
      'csv',
      'GML2',
      'gml3',
      'gml32',
      'json',
      'KML',
      // 'SHAPE-ZIP',
      'text/xml; subtype=gml/2.1.2',
      'text/xml; subtype=gml/3.1.1',
      'text/xml; subtype=gml/3.2'
    ]

    static final def geometryOperands = [
      'gml:Envelope',
      'gml:Point',
      'gml:LineString',
      'gml:Polygon'
    ]

    static final def spatialOperators = [
      'BBOX',
      'Beyond',
      'Contains',
      'Crosses',
      'Disjoint',
      'DWithin',
      'Equals',
      'Intersects',
      'Overlaps',
      'Touches',
      'Within'
    ]

    static final def comparisonOperators = [
      'Between',
      'EqualTo',
      'GreaterThan',
      'GreaterThanEqualTo',
      'LessThan',
      'LessThanEqualTo',
      'Like',
      'NotEqualTo',
      'NullCheck'
    ]

    static final Map<String, String> typeMappings = [
        'java.lang.Boolean': 'xsd:boolean',
        'java.math.BigDecimal': 'xsd:decimal',
        'Double': 'xsd:double',
        'Integer': 'xsd:int',
        'Long': 'xsd:long',
        'MultiLineString': 'gml:MultiLineStringPropertyType',
        'MultiPolygon': 'gml:MultiPolygonPropertyType',
        'Polygon': 'gml:PolygonPropertyType',
        'Point': 'gml:PointPropertyType',
        'String': 'xsd:string',
        'java.sql.Timestamp': 'xsd:dateTime'
    ]

    def getCapabilities(GetCapabilitiesRequest wfsParams)
    {
      def schemaLocation = grailsLinkGenerator.serverBaseURL
      def wfsEndpoint = grailsLinkGenerator.link( absolute: true, uri: '/wfs' )
      def model = geoscriptService.capabilitiesData

      def requestType = "GET"
      def requestMethod = "GetCapabilities"
      Date startTime = new Date()
      def responseTime
      def requestInfoLog

      def x = {
        mkp.xmlDeclaration()
        mkp.declareNamespace(ogcNamespaces)
        mkp.declareNamespace(model?.featureTypeNamespacesByPrefix)
        wfs.WFS_Capabilities( version:'1.1.0', xmlns: 'http://www.opengis.net/wfs',
          'xsi:schemaLocation': "http://www.opengis.net/wfs ${schemaLocation}/schemas/wfs/1.1.0/wfs.xsd",
        ) {
        ows.ServiceIdentification {
          ows.Title('O2 WFS Server')
          ows.Abstract('O2 WFS server')
          ows.Keywords {
            ows.Keyword('WFS')
            ows.Keyword('WMS')
            ows.Keyword('OMAR')
          }
          ows.ServiceType('WFS')
          ows.ServiceTypeVersion('1.1.0')
          ows.Fees('NONE')
          ows.AccessConstraints('NONE')
        }
        ows.ServiceProvider {
          ows.ProviderName('OSSIM Labs')
          ows.ServiceContact {
            ows.IndividualName('Scott Bortman')
            ows.PositionName('OMAR Developer')
            ows.ContactInfo {
              ows.Phone {
                ows.Voice()
                ows.Facsimile()
              }
              ows.Address {
                ows.DeliveryPoint()
                ows.City()
                ows.AdministrativeArea()
                ows.PostalCode()
                ows.Country()
                ows.ElectronicMailAddress()
              }
            }
          }
        }
        ows.OperationsMetadata {
          ows.Operation( name: 'GetCapabilities' ) {
            ows.DCP {
              ows.HTTP {
                ows.Get( 'xlink:href': wfsEndpoint )
                ows.Post( 'xlink:href': wfsEndpoint )
              }
            }
            ows.Parameter( name: 'AcceptVersions' ) {
              ows.Value('1.1.0')
            }
            ows.Parameter( name: 'AcceptFormats' ) {
              ows.Value('text/xml')
            }
          }
          ows.Operation( name: 'DescribeFeatureType' ) {
            ows.DCP {
              ows.HTTP {
                ows.Get( 'xlink:href': wfsEndpoint)
                ows.Post( 'xlink:href': wfsEndpoint)
              }
            }
            ows.Parameter( name: 'outputFormat' ) {
              ows.Value('text/xml; subtype=gml/3.1.1')
            }
          }
          ows.Operation( name: 'GetFeature' ) {
            ows.DCP {
              ows.HTTP {
                ows.Get( 'xlink:href': wfsEndpoint )
                ows.Post( 'xlink:href': wfsEndpoint )
              }
            }
            ows.Parameter( name: 'resultType' ) {
              ows.Value('results')
              ows.Value('hits')
            }
            ows.Parameter( name: 'outputFormat' ) {
              outputFormats?.each { outputFormat ->
                ows.Value(outputFormat)
              }
            }
          }
        }
        FeatureTypeList {
          Operations {
            Operation('Query')
          }
          model?.featureTypes?.each { featureType ->
            FeatureType( "xmlns:${featureType.namespace.prefix}":  featureType.namespace.uri) {
              Name("${featureType.namespace.prefix}:${featureType.name}")
              Title(featureType.title)
              Abstract(featureType.description)
              ows.Keywords {
                featureType.keywords.each { keyword ->
                  ows.Keyword(keyword)
                }
              }
              DefaultSRS("urn:x-ogc:def:crs:${featureType.proj}")
              ows.WGS84BoundingBox {
                def bounds = featureType.geoBounds
                ows.LowerCorner("${bounds.minX} ${bounds.minX}")
                ows.UpperCorner("${bounds.maxX} ${bounds.maxY}")
              }
            }
          }
        }
        ogc.Filter_Capabilities {
            ogc.Spatial_Capabilities {
              ogc.GeometryOperands {
                geometryOperands.each { geometryOperand ->
                  ogc.GeometryOperand(geometryOperand)
                }
              }
              ogc.SpatialOperators {
                spatialOperators.each { spatialOperator ->
                  ogc.SpatialOperator( name: spatialOperator )
                }
              }
            }
            ogc.Scalar_Capabilities {
              ogc.LogicalOperators()
              ogc.ComparisonOperators {
                comparisonOperators.each { comparisonOperator ->
                  ogc.ComparisonOperator(comparisonOperator)
                }
              }
              ogc.ArithmeticOperators {
                ogc.SimpleArithmetic()
                ogc.Functions {
                  ogc.FunctionNames {
                    model?.functions.each {function ->
                      ogc.FunctionName( nArgs: function.argCount, function.name)
                    }
                  }
                }
              }
            }
            ogc.Id_Capabilities {
              ogc.FID()
              ogc.EID()
            }
          }
        }
      }

      def xml = new StreamingMarkupBuilder( encoding: 'utf-8' ).bind( x )
      def contentType = 'application/xml'

      Date endTime = new Date()
      responseTime = Math.abs(startTime.getTime() - endTime.getTime())

      requestInfoLog = new JsonBuilder(timestamp: startTime.format("yyyy-MM-dd hh:mm:ss.ms"), requestType: requestType,
              requestMethod: requestMethod, endTime: endTime.format("yyyy-MM-dd hh:mm:ss.ms"), responseTime: responseTime,
              responseSize: xml.toString().bytes.length, contentType: contentType, params: wfsParams.toString())

      log.info requestInfoLog.toString()

      [contentType: contentType, text: xml.toString()]
    }

    def describeFeatureType(DescribeFeatureTypeRequest wfsParams)
    {
      def schema = geoscriptService.getSchemaInfoByTypeName(wfsParams.typeName)
      def schemaLocation = grailsLinkGenerator.serverBaseURL

      def requestType = "GET"
      def requestMethod = "DescribeFeatureType"
      Date startTime = new Date()
      def responseTime
      def requestInfoLog

      def x = {
        mkp.xmlDeclaration()
        mkp.declareNamespace(
          gml: "http://www.opengis.net/gml",
          xsd: "http://www.w3.org/2001/XMLSchema",
          (schema?.namespace?.prefix): schema?.namespace?.uri
        )
        xsd.schema( elementFormDefault: "qualified",
                    targetNamespace: schema?.namespace?.uri
        ) {
          xsd.import( namespace: "http://www.opengis.net/gml",
              schemaLocation: "${schemaLocation}/schemas/gml/3.1.1/base/gml.xsd" )
          xsd.complexType( name: "${schema?.name}Type" ) {
          xsd.complexContent {
            xsd.extension( base: "gml:AbstractFeatureType" ) {
              xsd.sequence {
                schema?.attributes?.each { attribute ->
                  xsd.element( maxOccurs: attribute?.maxOccurs,
                     minOccurs: attribute?.minOccurs,
                     name: attribute?.name,
                     nillable: attribute?.nillable,
                     type: typeMappings.get( attribute.type, attribute.type ) )
                  }
              }
            }
          }
        }
        xsd.element( name: schema?.name, substitutionGroup: "gml:_Feature",
                     type: "${schema?.namespace?.prefix}:${schema?.name}Type" )
      }
    }

      def xml = new StreamingMarkupBuilder( encoding: 'utf-8' ).bind( x )
      def contentType = 'text/xml'

      Date endTime = new Date()
      responseTime = Math.abs(startTime.getTime() - endTime.getTime())

      requestInfoLog = new JsonBuilder(timestamp: startTime.format("yyyy-MM-dd hh:mm:ss.ms"), requestType: requestType,
              requestMethod: requestMethod, endTime: endTime.format("yyyy-MM-dd hh:mm:ss.ms"), responseTime: responseTime,
              responseSize: xml.toString().bytes.length, contentType: contentType, params: wfsParams.toString())

      log.info requestInfoLog.toString()

      [contentType: contentType, text: xml.toString()]
    }

    def getFeature(GetFeatureRequest wfsParams)
    {
      def (prefix, layerName) = wfsParams?.typeName?.split(':')
      def options = parseOptions(wfsParams)
      def format = parseOutputFormat(wfsParams?.outputFormat)

      println "#" * 40
      println "WFS OPTIONS: ${options}"

      def requestType = "GET"
      def requestMethod = "GetFeature"
      Date startTime = new Date()
      def responseTime
      def responseSize
      def requestInfoLog
      def status
      def filter = options?.filter
      def maxFeatures = options?.max
      String userToken = options?.userToken

      println "USER TOKEN: ${userToken}"

      def results = geoscriptService.queryLayer(
        wfsParams?.typeName,
        options,
        wfsParams?.resultType ?: 'results',
        parseOutputFormat(wfsParams?.outputFormat)
      )

      def odsResults = getOdsResults(results, userToken)

      def intersection = getIntersection(results, odsResults)

      //TODO replace results with intersection
      def formattedResults

      switch (format)
      {
      case 'GML2':
      case 'GML3':
      case 'GML3_2':
        formattedResults = getFeatureGML(intersection, wfsParams?.typeName)
        break
      case 'JSON':
        formattedResults = getFeatureJSON(intersection, wfsParams?.typeName)
        break
      case 'CSV':
        formattedResults = [contentType: 'text/csv', text: intersection.features]
        break
      default:
        formattedResults = intersection
      }

      Date endTime = new Date()
      responseTime = Math.abs(startTime.getTime() - endTime.getTime())

      status = results != null ? 200 : 400
      responseSize = formattedResults.toString().bytes.length

      requestInfoLog = new JsonBuilder(timestamp: startTime.format("yyyy-MM-dd hh:mm:ss.ms"), requestType: requestType,
              requestMethod: requestMethod, status: status, endTime: endTime.format("yyyy-MM-dd hh:mm:ss.ms"),
              responseTime: responseTime, responseSize: responseSize, filter: filter, maxFeatures: maxFeatures,
              numberOfFeatures: intersection?.numberOfFeatures, numberMatched: intersection?.numberMatched, params: wfsParams.toString())

      log.info requestInfoLog.toString()

      formattedResults
    }

  def getIntersection(def wfsResults, def odsResults)
  {
      //TODO implement intersection comparison of wfs and ods results
      def intersection = "I'M THE INTERSECTION"
      return intersection
  }

  def getOdsResults(def wfsResults, String userToken)
  {
    //TODO implement map of field keys to values
    // contentIdentifiers = results.features.ids.each -> { it.collect }
    def odsFields = [test: test]
    odsClientService.metadatainfosByField(odsFields, userToken)
  }

  def getFeatureGML(def results, def typeName, def version='1.1.0')
  {
    def schemaLocation = grailsLinkGenerator.serverBaseURL

    def describeFeatureTypeURL = grailsLinkGenerator.link(params: [
      service: 'WFS',
      version: version,
      request: 'DescribeFeatureType',
      typeName: typeName
    ], absolute: true, controller: 'wfs')

    def x = {
      mkp.xmlDeclaration()
      mkp.declareNamespace(ogcNamespaces)
      mkp.declareNamespace((results?.namespace?.prefix): results?.namespace?.uri)
      wfs.FeatureCollection(
        numberOfFeatures: results?.numberOfFeatures,
        numberMatched: results?.numberMatched,
        timeStamp: results?.timeStamp,
        'xsi:schemaLocation': "${results?.namespace?.uri} ${describeFeatureTypeURL} http://www.opengis.net/wfs ${schemaLocation}/schemas/wfs/1.1.0/wfs.xsd"
      ) {
        if ( results?.features) {
          gml.featureMembers {
              results?.features?.each { feature ->
              mkp.yieldUnescaped(feature)
            }
          }
        }
      }
    }

    def xml = new StreamingMarkupBuilder( encoding: 'utf-8' ).bind( x )
    def contentType = 'application/xml'

    [contentType: 'text/xml', text: xml.toString()]
  }

  def getFeatureJSON(def results, def typeName, def version='1.1.0')
  {
    def slurper = new JsonSlurper()

    def x = {
      type 'FeatureCollection'
      totalFeatures results?.numberOfFeatures
       features results?.features?.collect {
            if ( it instanceof String ) {
              slurper.parseText(it)
            } else {
              it
            }
       }
       crs {
         type 'name'
         properties {
           name 'urn:ogc:def:crs:EPSG::4326'
         }
       }
     }

     def jsonWriter = new StringWriter()
     def jsonBuilder = new StreamingJsonBuilder(jsonWriter)
     jsonBuilder(x)

    [contentType: 'application/json', text: jsonWriter.toString()]
  }

  def parseOptions(def wfsParams)
  {
    def wfsParamNames = [
        'maxFeatures', 'startIndex', 'propertyName', 'sortBy', 'filter', 'userToken'
    ]

    def options = wfsParamNames.inject( [:] ) { options, wfsParamName ->
      if ( wfsParams[wfsParamName] != null )
      {
        switch ( wfsParamName )
        {
        case 'maxFeatures':
          options['max'] = wfsParams[wfsParamName]
          break
        case 'startIndex':
          options['start'] = wfsParams[wfsParamName]
          break
        case 'propertyName':
          def fields = wfsParams[wfsParamName]?.split( ',' )?.collect {
            it.split( ':' )?.last()
          } as List<String>
          if ( fields && !fields?.isEmpty() && fields?.every { it } )
          {
            // println "FIELDS: ${fields.size()}"
            options['fields'] = fields
          }
          break
        case 'sortBy':
          if ( wfsParams[wfsParamName]?.trim() )
          {
            options['sort'] = wfsParams[wfsParamName].split(',').collect {
                def x = it.split(/ |\+/) as List
                if ( x[1] ==~ /.*D(ESC)?/ ) {
                    x = [x[0], 'DESC'] as List
                } else if (x[1] ==~ /.*A(SC)?/) {
                    x = [x[0], 'ASC'] as List
                }
            } as List
          }
          break
        case 'userToken':
          options['userToken'] = wfsParams[wfsParamName]
          break
        default:
          if ( wfsParams[wfsParamName] )
          {
            options[wfsParamName] = wfsParams[wfsParamName]
          }
        }
      }
      options
    }

    options
  }

  def parseOutputFormat(def outputFormat)
  {
    def format = null

    switch ( outputFormat?.toUpperCase() )
    {
    case 'GML3':
    case 'TEXT/XML; SUBTYPE=GML/3.1.1':
      format = 'GML3'
      break
    case 'GML2':
    case 'TEXT/XML; SUBTYPE=GML/2.1.2':
      format = 'GML2'
      break
    case 'GML32':
    case 'TEXT/XML; SUBTYPE=GML/3.2':
      format = 'GML3_2'
      break
    case 'APPLICATION/JSON':
    case 'JSON':
          format = 'JSON'
          break
    case 'APPLICATION/CSV':
    case 'CSV':
          format = 'CSV'
          break
    case 'KML':
    case 'APPLICATION/VND.GOOGLE-EARTH.KMLl+XML':
    case 'APPLICATION/VND.GOOGLE-EARTH.KMLl XML':
          format = 'KML'
          break
    }

    format
  }
}
