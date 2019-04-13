package services

import java.util.Date

import api.Permission.Permission
import models._

import scala.collection.mutable.ListBuffer
import scala.util.Try

trait GroupService {

  def insert(group: Group) : Option[String]
}
