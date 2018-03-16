package services

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

/**
  * Default production module.
  */
class ConfigurationModule() extends Module {
  def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = {
    Seq(
      bind(classOf[MongoService]).to(classOf[services.mongodb.MongoSalatPlugin]),
      bind(classOf[UserService]).to(classOf[services.mongodb.MongoDBUserService]),
      bind(classOf[AppConfigurationService]).to(classOf[services.mongodb.MongoDBAppConfigurationService]),
      bind(classOf[ByteStorageService]).to(classOf[services.mongodb.MongoDBByteStorage]),
      bind(classOf[DatasetService]).to(classOf[services.mongodb.MongoDBDatasetService]),
      bind(classOf[FileService]).to(classOf[services.mongodb.MongoDBFileService]),
      bind(classOf[SpaceService]).to(classOf[services.mongodb.MongoDBSpaceService]),
      bind(classOf[MultimediaQueryService]).to(classOf[services.mongodb.MongoDBMultimediaQueryService]),
      bind(classOf[CollectionService]).to(classOf[services.mongodb.MongoDBCollectionService]),
      bind(classOf[TagService]).to(classOf[services.mongodb.MongoDBTagService]),
      bind(classOf[ExtractorService]).to(classOf[services.mongodb.MongoDBExtractorService]),
      bind(classOf[ExtractionRequestsService]).to(classOf[services.mongodb.MongoDBExtractionRequestsService]),
      bind(classOf[SectionService]).to(classOf[services.mongodb.MongoDBSectionService]),
      bind(classOf[CommentService]).to(classOf[services.mongodb.MongoDBCommentService]),
      bind(classOf[PreviewService]).to(classOf[services.mongodb.MongoDBPreviewService]),
      bind(classOf[ExtractionService]).to(classOf[services.mongodb.MongoDBExtractionService]),
      bind(classOf[TempFileService]).to(classOf[services.mongodb.MongoDBTempFileService]),
      bind(classOf[ThreeDService]).to(classOf[services.mongodb.MongoDBThreeDService]),
      bind(classOf[RdfSPARQLService]).to(classOf[services.fourstore.FourStoreRdfSPARQLService]),
      bind(classOf[ThumbnailService]).to(classOf[services.mongodb.MongoDBThumbnailService]),
      bind(classOf[TileService]).to(classOf[services.mongodb.MongoDBTileService]),
      bind(classOf[SectionIndexInfoService]).to(classOf[services.mongodb.MongoDBSectionIndexInfoService]),
      bind(classOf[RelationService]).to(classOf[services.mongodb.MongoDBRelationService]),
      bind(classOf[ContextLDService]).to(classOf[services.mongodb.MongoDBContextLDService]),
      bind(classOf[EventService]).to(classOf[services.mongodb.MongoDBEventService]),
      bind(classOf[SchedulerService]).to(classOf[services.mongodb.MongoDBSchedulerService]),
      bind(classOf[CurationService]).to(classOf[services.mongodb.MongoDBCurationService]),
      bind(classOf[MetadataService]).to(classOf[services.mongodb.MongoDBMetadataService]),
      bind(classOf[FolderService]).to(classOf[services.mongodb.MongoDBFolderService]),
      bind(classOf[LogoService]).to(classOf[services.mongodb.MongoDBLogoService]),
      bind(classOf[VocabularyService]).to(classOf[services.mongodb.MongoDBVocabularyService]),
      bind(classOf[VocabularyTermService]).to(classOf[services.mongodb.MongoDBVocabularyTermService]),
      bind(classOf[SelectionService]).to(classOf[services.mongodb.MongoDBSelectionService]),
      bind(classOf[SecureSocialUserService]).to(classOf[services.mongodb.MongoDBSecureSocialUserService]),
      bind(classOf[PolyglotService]).to(classOf[services.PolyglotServiceImpl]),
      bind(classOf[ToolManagerService]).to(classOf[services.ToolManagerServiceImpl]),
      bind(classOf[GeostreamsService]).to(classOf[services.GeostreamsServicePostgresImpl]),
      bind(classOf[TempFilesService]).to(classOf[services.TempFilesServiceImpl]),
      bind(classOf[FileDumpService]).to(classOf[services.FileDumpServiceImpl]),
      bind(classOf[MailService]).to(classOf[services.MailServiceImpl]),
      bind(classOf[ElasticsearchService]).to(classOf[services.ElasticsearchServiceImpl]),
      bind(classOf[VersusService]).to(classOf[services.VersusServiceImpl]),
      bind(classOf[RabbitMQService]).to(classOf[services.RabbitmqPlugin]),
      bind(classOf[RDFExportService]).to(classOf[services.RDFExportServiceImpl]),
      bind(classOf[DatasetsAutodumpService]).to(classOf[services.DatasetsAutodumpServiceImpl])
    )
  }
}
