package services

import com.google.inject.AbstractModule
import play.api.inject.{Binding, Module}
import play.api.{Configuration, Environment, Play}
import services.mongodb.{MongoService, SecureSocialUserService}

/**
  * Guide module configuration.
  *
  *
  */
object DI {
  lazy val injector = Play.current.injector
}

class ConfigurationModule extends AbstractModule {
  protected def configure(): Unit = {
    bind(classOf[ApplicationStart]).asEagerSingleton()
    bind(classOf[MongoService]).to(get("service.MongoService", "services.mongodb.MongoSalatPlugin"))
    bind(classOf[AppConfigurationService]).to(get("service.appConfiguration", "services.mongodb.MongoDBAppConfigurationService"))
    bind(classOf[UserService]).to(get("service.users", "services.mongodb.MongoDBUserService"))
    // ByteStorageService is used to store the actual bytes
    bind(classOf[ByteStorageService]).to(get("service.byteStorage", "services.mongodb.MongoDBByteStorage"))
    bind(classOf[DatasetService]).to(get("service.datasets", "services.mongodb.MongoDBDatasetService"))
    bind(classOf[FileService]).to(get("service.files", "services.mongodb.MongoDBFileService"))
    bind(classOf[SpaceService]).to(get("service.spaces", "services.mongodb.MongoDBSpaceService"))
    bind(classOf[MultimediaQueryService]).to(get("service.multimediaQuery", "services.mongodb.MongoDBMultimediaQueryService"))
    bind(classOf[CollectionService]).to(get("service.collections", "services.mongodb.MongoDBCollectionService"))
    bind(classOf[TagService]).to(get("service.tags", "services.mongodb.MongoDBTagService"))
    bind(classOf[ExtractorService]).to(get("service.extractors", "services.mongodb.MongoDBExtractorService"))
    bind(classOf[ExtractionRequestsService]).to(get("service.extractionRequests", "services.mongodb.MongoDBExtractionRequestsService"))
    bind(classOf[SectionService]).to(get("service.sections", "services.mongodb.MongoDBSectionService"))
    bind(classOf[CommentService]).to(get("service.comments", "services.mongodb.MongoDBCommentService"))
    bind(classOf[PreviewService]).to(get("service.previews", "services.mongodb.MongoDBPreviewService"))
    bind(classOf[ExtractionService]).to(get("service.extractions", "services.mongodb.MongoDBExtractionService"))
    bind(classOf[TempFileService]).to(get("service.tempFiles", "services.mongodb.MongoDBTempFileService"))
    bind(classOf[ThumbnailService]).to(get("service.thumbnails", "services.mongodb.MongoDBThumbnailService"))
    bind(classOf[TileService]).to(get("service.tiles", "services.mongodb.MongoDBTileService"))
    bind(classOf[SectionIndexInfoService]).to(get("service.sectionIndexInfo", "services.mongodb.MongoDBSectionIndexInfoService"))
    bind(classOf[RelationService]).to(get("service.relations", "services.mongodb.MongoDBRelationService"))
    bind(classOf[ContextLDService]).to(get("service.ContextLDService", "services.mongodb.MongoDBContextLDService"))
    bind(classOf[EventService]).to(get("service.events", "services.mongodb.MongoDBEventService"))
    bind(classOf[SchedulerService]).to(get("service.jobs", "services.mongodb.MongoDBSchedulerService"))
    bind(classOf[CurationService]).to(get("service.curationObjs", "services.mongodb.MongoDBCurationService"))
    bind(classOf[MetadataService]).to(get("service.metadata", "services.mongodb.MongoDBMetadataService"))
    bind(classOf[FolderService]).to(get("service.folders", "services.mongodb.MongoDBFolderService"))
    bind(classOf[LogoService]).to(get("service.logos", "services.mongodb.MongoDBLogoService"))
    bind(classOf[VocabularyService]).to(get("service.vocabularies", "services.mongodb.MongoDBVocabularyService"))
    bind(classOf[VocabularyTermService]).to(get("service.vocabularyterms", "services.mongodb.MongoDBVocabularyTermService"))
    bind(classOf[StandardVocabularyService]).to(get("service.standardvocabulary", "services.mongodb.MongoDBStandardVocabularyService"))
    bind(classOf[SelectionService]).to(get("service.select", "services.mongodb.MongoDBSelectionService"))
    bind(classOf[FileLinkService]).to(get("service.FileLinkService", "services.mongodb.MongoDBFileLinkService"))
    bind(classOf[SearchService]).to(get("service.SearchService", "services.elasticsearch.ElasticsearchSearchService"))
    bind(classOf[QueueService]).to(get("service.QueueService", "services.mongodb.MongoDBQueueService"))
    bind(classOf[ExtractionBusService]).to(get("service.ExtractionBusService", "services.rabbitmq.RabbitmqExtractionBusService"))
  }

  protected def get[T](key: String, missing: String) : Class[T] = {
    val name = current.configuration.getString(key).getOrElse(missing)
    Class.forName(name).asInstanceOf[Class[T]]
  }
}

/**
  * Default production module.
  */
//class ConfigurationModule() extends Module {
//  def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = {
//    Seq(
//      bind(classOf[MongoService]).to(classOf[services.mongodb.MongoSalatPlugin]),
//      bind(classOf[UserService]).to(classOf[services.mongodb.MongoDBUserService]),
//      bind(classOf[AppConfigurationService]).to(classOf[services.mongodb.MongoDBAppConfigurationService]),
//      bind(classOf[ByteStorageService]).to(classOf[services.mongodb.MongoDBByteStorage]),
//      bind(classOf[DatasetService]).to(classOf[services.mongodb.MongoDBDatasetService]),
//      bind(classOf[FileService]).to(classOf[services.mongodb.MongoDBFileService]),
//      bind(classOf[SpaceService]).to(classOf[services.mongodb.MongoDBSpaceService]),
//      bind(classOf[MultimediaQueryService]).to(classOf[services.mongodb.MongoDBMultimediaQueryService]),
//      bind(classOf[CollectionService]).to(classOf[services.mongodb.MongoDBCollectionService]),
//      bind(classOf[TagService]).to(classOf[services.mongodb.MongoDBTagService]),
//      bind(classOf[ExtractorService]).to(classOf[services.mongodb.MongoDBExtractorService]),
//      bind(classOf[ExtractionRequestsService]).to(classOf[services.mongodb.MongoDBExtractionRequestsService]),
//      bind(classOf[SectionService]).to(classOf[services.mongodb.MongoDBSectionService]),
//      bind(classOf[CommentService]).to(classOf[services.mongodb.MongoDBCommentService]),
//      bind(classOf[PreviewService]).to(classOf[services.mongodb.MongoDBPreviewService]),
//      bind(classOf[ExtractionService]).to(classOf[services.mongodb.MongoDBExtractionService]),
//      bind(classOf[TempFileService]).to(classOf[services.mongodb.MongoDBTempFileService]),
//      bind(classOf[ThreeDService]).to(classOf[services.mongodb.MongoDBThreeDService]),
//      bind(classOf[RdfSPARQLService]).to(classOf[services.fourstore.FourStoreRdfSPARQLService]),
//      bind(classOf[ThumbnailService]).to(classOf[services.mongodb.MongoDBThumbnailService]),
//      bind(classOf[TileService]).to(classOf[services.mongodb.MongoDBTileService]),
//      bind(classOf[SectionIndexInfoService]).to(classOf[services.mongodb.MongoDBSectionIndexInfoService]),
//      bind(classOf[RelationService]).to(classOf[services.mongodb.MongoDBRelationService]),
//      bind(classOf[ContextLDService]).to(classOf[services.mongodb.MongoDBContextLDService]),
//      bind(classOf[EventService]).to(classOf[services.mongodb.MongoDBEventService]),
//      bind(classOf[SchedulerService]).to(classOf[services.mongodb.MongoDBSchedulerService]),
//      bind(classOf[CurationService]).to(classOf[services.mongodb.MongoDBCurationService]),
//      bind(classOf[MetadataService]).to(classOf[services.mongodb.MongoDBMetadataService]),
//      bind(classOf[FolderService]).to(classOf[services.mongodb.MongoDBFolderService]),
//      bind(classOf[LogoService]).to(classOf[services.mongodb.MongoDBLogoService]),
//      bind(classOf[VocabularyService]).to(classOf[services.mongodb.MongoDBVocabularyService]),
//      bind(classOf[VocabularyTermService]).to(classOf[services.mongodb.MongoDBVocabularyTermService]),
//      bind(classOf[SelectionService]).to(classOf[services.mongodb.MongoDBSelectionService]),
//      bind(classOf[SecureSocialUserService]).to(classOf[services.mongodb.MongoDBSecureSocialUserService]),
//      bind(classOf[PolyglotService]).to(classOf[services.PolyglotServiceImpl]),
//      bind(classOf[ToolManagerService]).to(classOf[services.ToolManagerServiceImpl]),
//      bind(classOf[GeostreamsService]).to(classOf[services.GeostreamsServicePostgresImpl]),
//      bind(classOf[TempFilesService]).to(classOf[services.TempFilesServiceImpl]),
//      bind(classOf[FileDumpService]).to(classOf[services.FileDumpServiceImpl]),
//      bind(classOf[MailService]).to(classOf[services.MailServiceImpl]),
//      bind(classOf[ElasticsearchService]).to(classOf[services.ElasticsearchServiceImpl]),
//      bind(classOf[VersusService]).to(classOf[services.VersusServiceImpl]),
//      bind(classOf[RabbitMQService]).to(classOf[services.RabbitmqPlugin]),
//      bind(classOf[RDFExportService]).to(classOf[services.RDFExportServiceImpl]),
//      bind(classOf[DatasetsAutodumpService]).to(classOf[services.DatasetsAutodumpServiceImpl])
//    )
//  }
//}
