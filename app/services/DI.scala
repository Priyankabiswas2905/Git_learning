package services

import play.api.Play.current
import play.api.Play
import com.google.inject.Guice
import com.google.inject.AbstractModule
import services.mongodb._
import services.fourstore.FourStoreRdfSPARQLService

/**
 * Guice module configuration.
 *
 * @author Luigi Marini
 *
 */
object DI {
    lazy val injector = {
	    Play.isProd match {
	      case true => Guice.createInjector(new ProdModule)
	      case false => Guice.createInjector(new DevModule)
	    }
  }
}

/**
 * Default production module.
 */
class ProdModule extends AbstractModule {
  protected def configure() {
    bind(classOf[DatasetService]).to(classOf[MongoDBDatasetService])
    bind(classOf[FileService]).to(classOf[MongoDBFileService])
    bind(classOf[MultimediaQueryService]).to(classOf[MongoDBMultimediaQueryService])
    bind(classOf[CollectionService]).to(classOf[MongoDBCollectionService])
    bind(classOf[TagService]).to(classOf[MongoDBTagService])
    bind(classOf[SectionService]).to(classOf[MongoDBSectionService])
    bind(classOf[CommentService]).to(classOf[MongoDBCommentService])
    bind(classOf[PreviewService]).to(classOf[MongoDBPreviewService])
    bind(classOf[AppConfigurationService]).to(classOf[MongoDBAppConfigurationService])
    bind(classOf[AppAppearanceService]).to(classOf[MongoDBAppAppearanceService])
    bind(classOf[ExtractionService]).to(classOf[MongoDBExtractionService])
    bind(classOf[TempFileService]).to(classOf[MongoDBTempFileService])
    bind(classOf[ThreeDService]).to(classOf[MongoDBThreeDService])
    bind(classOf[RdfSPARQLService]).to(classOf[FourStoreRdfSPARQLService])
    bind(classOf[ThumbnailService]).to(classOf[MongoDBThumbnailService])
    bind(classOf[TileService]).to(classOf[MongoDBTileService])
    bind(classOf[SocialUserService]).to(classOf[MongoDBSocialUserService])
    bind(classOf[SubscriberService]).to(classOf[MongoDBSubscriberService])
    bind(classOf[UserAccessRightsService]).to(classOf[MongoDBUserAccessRightsService])
    bind(classOf[GateOneService]).to(classOf[MongoDBGateOneService])
  }
}

/**
 * Default development module.
 */
class DevModule extends AbstractModule {
  protected def configure() {
    bind(classOf[DatasetService]).to(classOf[MongoDBDatasetService])
    bind(classOf[FileService]).to(classOf[MongoDBFileService])
    bind(classOf[MultimediaQueryService]).to(classOf[MongoDBMultimediaQueryService])
    bind(classOf[CollectionService]).to(classOf[MongoDBCollectionService])
    bind(classOf[TagService]).to(classOf[MongoDBTagService])
    bind(classOf[SectionService]).to(classOf[MongoDBSectionService])
    bind(classOf[CommentService]).to(classOf[MongoDBCommentService])
    bind(classOf[PreviewService]).to(classOf[MongoDBPreviewService])
    bind(classOf[AppConfigurationService]).to(classOf[MongoDBAppConfigurationService])
    bind(classOf[AppAppearanceService]).to(classOf[MongoDBAppAppearanceService])
    bind(classOf[ExtractionService]).to(classOf[MongoDBExtractionService])
    bind(classOf[TempFileService]).to(classOf[MongoDBTempFileService])
    bind(classOf[ThreeDService]).to(classOf[MongoDBThreeDService])
    bind(classOf[RdfSPARQLService]).to(classOf[FourStoreRdfSPARQLService])
    bind(classOf[ThumbnailService]).to(classOf[MongoDBThumbnailService])
    bind(classOf[TileService]).to(classOf[MongoDBTileService])
    bind(classOf[SocialUserService]).to(classOf[MongoDBSocialUserService])
    bind(classOf[SubscriberService]).to(classOf[MongoDBSubscriberService])
    bind(classOf[UserAccessRightsService]).to(classOf[MongoDBUserAccessRightsService])
    bind(classOf[GateOneService]).to(classOf[MongoDBGateOneService])
  }
}
