import javax.inject.Inject
import play.api.http.DefaultHttpFilters
import play.api.libs.Jsonp
import play.filters.cors.CORSFilter
import play.filters.gzip.GzipFilter

class Filters @Inject() (gzipFilter: GzipFilter, corsFilter: CORSFilter)
  extends DefaultHttpFilters(gzipFilter, corsFilter)
