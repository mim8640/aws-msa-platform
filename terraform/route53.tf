# ============================================================
# Route53
# renuvo.store Hosted Zone
# ============================================================

data "aws_route53_zone" "renuvo" {
  name         = "renuvo.store"
  private_zone = false
}


# ============================================================
# API
# api.renuvo.store -> NGINX Ingress Load Balancer
# ============================================================

resource "aws_route53_record" "api" {
  zone_id = data.aws_route53_zone.renuvo.zone_id
  name    = "api.renuvo.store"
  type    = "CNAME"
  ttl     = 300

  records = [
    "a601fb68ad771467d896fd9a028ef580-831148130.ap-northeast-2.elb.amazonaws.com"
  ]
}


# ============================================================
# Frontend
# www.renuvo.store -> NGINX Ingress Load Balancer
# ============================================================

resource "aws_route53_record" "www" {
  zone_id = data.aws_route53_zone.renuvo.zone_id
  name    = "www.renuvo.store"
  type    = "CNAME"
  ttl     = 300

  records = [
    "a601fb68ad771467d896fd9a028ef580-831148130.ap-northeast-2.elb.amazonaws.com"
  ]
}