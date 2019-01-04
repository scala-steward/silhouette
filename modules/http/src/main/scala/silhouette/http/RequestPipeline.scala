/**
 * Licensed to the Minutemen Group under one or more contributor license
 * agreements. See the COPYRIGHT file distributed with this work for
 * additional information regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License. You may
 * obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package silhouette.http

import java.net.{ URI, URLEncoder }

import silhouette.crypto.Hash
import silhouette.crypto.Hash._

/**
 * Decorates a framework specific request implementation.
 *
 * Frameworks should create an implicit conversion between the implementation of this pipeline and
 * the Framework specific request instance.
 *
 * @tparam R The type of the request.
 */
protected[silhouette] trait RequestPipeline[R] extends RequestExtractor[R] {

  /**
   * The framework specific request implementation.
   */
  val request: R

  /**
   * Gets the absolute URI of the request target.
   *
   * This must contain the absolute URI of thr request target, because we need this to resolve relative URIs
   * against this.
   *
   * @return The absolute URI of the request target.
   */
  def uri: URI

  /**
   * Creates a new request pipeline with the given URI.
   *
   * This must contain the absolute URI of thr request target, because we need this to resolve relative URIs
   * against this URI.
   *
   * @param uri The absolute URI of the request target.
   * @return A new request pipeline instance with the set URI.
   */
  def withUri(uri: URI): RequestPipeline[R]

  /**
   * Gets the HTTP request method.
   *
   * @return The HTTP request method.
   */
  def method: Method

  /**
   * Creates a new request pipeline with the given HTTP request method.
   *
   * @param method The HTTP request method to set.
   * @return A new request pipeline instance with the set HTTP request method.
   */
  def withMethod(method: Method): RequestPipeline[R]

  /**
   * Gets all headers.
   *
   * @return All headers.
   */
  def headers: Seq[Header]

  /**
   * Gets the header for the given name.
   *
   * @param name The name of the header for which the header should be returned.
   * @return Some header for the given name, None if no header for the given name could be found.
   */
  def header(name: Header.Name): Option[Header] = headers.find(_.name == name)

  /**
   * Creates a new request pipeline with the given headers.
   *
   * This method must override any existing header with the same name. If multiple headers with the
   * same name are given to this method, then the values must be composed into a list.
   *
   * If a request holds the following headers, then this method must implement the following behaviour:
   * {{{
   *   Seq(
   *     Header("TEST1", Seq("value1", "value2")),
   *     Header("TEST2", "value1")
   *   )
   * }}}
   *
   * Append a new header:
   * {{{
   *   withHeaders(Header("TEST3", "value1"))
   *
   *   Seq(
   *     Header("TEST1" -> Seq("value1", "value2")),
   *     Header("TEST2" -> Seq("value1")),
   *     Header("TEST3" -> Seq("value1"))
   *   )
   * }}}
   *
   * Override the header `TEST1` with a new value:
   * {{{
   *   withHeaders(Header("TEST1", "value3"))
   *
   *   Seq(
   *     Header("TEST1", Seq("value3")),
   *     Header("TEST2", Seq("value1"))
   *   )
   * }}}
   *
   * Compose headers with the same name:
   * {{{
   *   withHeaders(Header("TEST1", "value3"), Header("TEST1", Seq("value4", "value5")))
   *
   *   Set(
   *     Header("TEST1", Seq("value3", "value4", "value5")),
   *     Header("TEST2", Seq("value1"))
   *   )
   * }}}
   *
   * @param headers The headers to set.
   * @return A new request pipeline instance with the set headers.
   */
  def withHeaders(headers: Header*): RequestPipeline[R]

  /**
   * Gets the list of cookies.
   *
   * @return The list of cookies.
   */
  def cookies: Seq[Cookie]

  /**
   * Gets a cookie.
   *
   * @param name The name for which the cookie should be returned.
   * @return Some cookie or None if no cookie for the given name could be found.
   */
  def cookie(name: String): Option[Cookie] = cookies.find(_.name == name)

  /**
   * Creates a new request pipeline with the given cookies.
   *
   * This method must override any existing cookie with the same name. If multiple cookies with the
   * same name are given to this method, then the last cookie in the list wins.
   *
   * If a request holds the following cookies, then this method must implement the following behaviour:
   * {{{
   *   Seq(
   *     Cookie("test1", "value1"),
   *     Cookie("test2", "value2")
   *   )
   * }}}
   *
   * Append a new cookie:
   * {{{
   *   withCookies(Cookie("test3", "value3"))
   *
   *   Seq(
   *     Cookie("test1", "value1"),
   *     Cookie("test2", "value2"),
   *     Cookie("test3", "value3")
   *   )
   * }}}
   *
   * Override the cookie `test1`:
   * {{{
   *   withCookies(Cookie("test1", "value3"))
   *
   *   Seq(
   *     Cookie("test1", "value3"),
   *     Cookie("test2", "value2")
   *   )
   * }}}
   *
   * Use the last cookie if multiple cookies with the same name are given:
   * {{{
   *   withCookies(Cookie("test1", "value3"), Cookie("test1", "value4"))
   *
   *   Seq(
   *     Cookie("test1", "value4"),
   *     Cookie("test2", "value2")
   *   )
   * }}}
   *
   * @param cookies The cookies to set.
   * @return A new request pipeline instance with the set cookies.
   */
  def withCookies(cookies: Cookie*): RequestPipeline[R]

  /**
   * Gets the raw query string.
   *
   * @return The raw query string.
   */
  def rawQueryString: String = {
    queryParams.foldLeft(List[String]()) {
      case (acc, (key, value)) =>
        acc :+ value.map(URLEncoder.encode(key, "UTF-8") + "=" + URLEncoder.encode(_, "UTF-8")).mkString("&")
    }.mkString("&")
  }

  /**
   * Gets all query params.
   *
   * While there is no definitive standard, most web frameworks allow duplicate params with the
   * same name. Therefore we must define a query param values as list of values.
   *
   * @return All query params.
   */
  def queryParams: Map[String, Seq[String]]

  /**
   * Gets the values for a query param.
   *
   * While there is no definitive standard, most web frameworks allow duplicate params with the
   * same name. Therefore we must define a query param values as list of values.
   *
   * @param name The name of the query param for which the values should be returned.
   * @return A list of param values for the given name or an empty list if no params for the given name could be found.
   */
  def queryParam(name: String): Seq[String] = queryParams.getOrElse(name, Nil)

  /**
   * Creates a new request pipeline with the given query params.
   *
   * This method must override any existing query param with the same name. If multiple query params with the
   * same name are given to this method, then the values must be composed into a list.
   *
   * If a request holds the following query params, then this method must implement the following behaviour:
   * {{{
   *   Map(
   *     "TEST1" -> Seq("value1", "value2"),
   *     "TEST2" -> Seq("value1")
   *   )
   * }}}
   *
   * Append a new query param:
   * {{{
   *   withQueryParams("test3" -> "value1")
   *
   *   Map(
   *     "test1" -> Seq("value1", "value2"),
   *     "test2" -> Seq("value1"),
   *     "test3" -> Seq("value1")
   *   )
   * }}}
   *
   * Override the query param `test1` with a new value:
   * {{{
   *   withQueryParams("test1" -> "value3")
   *
   *   Map(
   *     "test1" -> Seq("value3"),
   *     "test2" -> Seq("value1")
   *   )
   * }}}
   *
   * Compose query params with the same name:
   * {{{
   *   withQueryParams("test1" -> "value3", "test1" -> "value4")
   *
   *   Map(
   *     "test1" -> Seq("value3", "value4"),
   *     "test2" -> Seq("value1")
   *   )
   * }}}
   *
   * @param params The query params to set.
   * @return A new request pipeline instance with the set query params.
   */
  def withQueryParams(params: (String, String)*): RequestPipeline[R]

  /**
   * Generates a default fingerprint from common request headers.
   *
   * A generator which creates a SHA1 fingerprint from `User-Agent`, `Accept-Language` and `Accept-Charset` headers.
   *
   * The `Accept` header would also be a good candidate, but this header makes problems in applications
   * which uses content negotiation. So the default fingerprint generator doesn't include it.
   *
   * The same with `Accept-Encoding`. But in Chromium/Blink based browser the content of this header may
   * be changed during requests.
   *
   * @return A default fingerprint from the request.
   */
  def fingerprint: String = {
    Hash.sha1(new StringBuilder()
      .append(headers.find(_.name == Header.Name.`User-Agent`).map(_.value).getOrElse("")).append(":")
      .append(headers.find(_.name == Header.Name.`Accept-Language`).map(_.value).getOrElse("")).append(":")
      .append(headers.find(_.name == Header.Name.`Accept-Charset`).map(_.value).getOrElse(""))
      .toString()
    )
  }

  /**
   * Generates a fingerprint from request.
   *
   * @param generator A generator function to create a fingerprint from request.
   * @return A fingerprint of the client.
   */
  def fingerprint(generator: R => String): String = generator(request)

  /**
   * Indicates if the request is a secure HTTPS request.
   *
   * @return True if the request is a secure HTTPS request, false otherwise.
   */
  def isSecure: Boolean = uri.getScheme == "https"

  /**
   * Unboxes the framework specific request implementation.
   *
   * @return The framework specific request implementation.
   */
  def unbox: R
}
