/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2018 Axelor (<http://axelor.com>).
 *
 * This program is free software: you can redistribute it and/or  modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.axelor.apps.base.service;

import com.axelor.apps.base.db.Address;
import com.axelor.apps.base.db.Country;
import com.axelor.apps.base.db.repo.AddressRepository;
import com.axelor.apps.base.db.repo.PartnerRepository;
import com.axelor.apps.base.exceptions.IExceptionMessage;
import com.axelor.common.StringUtils;
import com.axelor.exception.AxelorException;
import com.axelor.i18n.I18n;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.persist.Transactional;
import com.opencsv.CSVWriter;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import wslite.json.JSONException;

@Singleton
public class AddressServiceImpl implements AddressService {

  @Inject private AddressRepository addressRepo;

  @Inject private com.axelor.apps.tool.address.AddressTool ads;

  @Inject private PartnerRepository partnerRepo;

  @Inject protected MapService mapService;

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Override
  public boolean check(String wsdlUrl) {
    return ads.doCanSearch(wsdlUrl);
  }

  @Override
  public Map<String, Object> validate(String wsdlUrl, String search) {
    return ads.doSearch(wsdlUrl, search);
  }

  @Override
  public com.qas.web_2005_02.Address select(String wsdlUrl, String moniker) {
    return ads.doGetAddress(wsdlUrl, moniker);
  }

  @Override
  public int export(String path) throws IOException {
    List<Address> addresses = addressRepo.all().filter("self.certifiedOk IS FALSE").fetch();

    CSVWriter csv =
        new CSVWriter(new java.io.FileWriter(path), "|".charAt(0), CSVWriter.NO_QUOTE_CHARACTER);
    List<String> header = new ArrayList<String>();
    header.add("Id");
    header.add("AddressL1");
    header.add("AddressL2");
    header.add("AddressL3");
    header.add("AddressL4");
    header.add("AddressL5");
    header.add("AddressL6");
    header.add("CodeINSEE");

    csv.writeNext(header.toArray(new String[header.size()]));
    List<String> items = new ArrayList<String>();
    for (Address a : addresses) {

      items.add(a.getId() != null ? a.getId().toString() : "");
      items.add(a.getAddressL2() != null ? a.getAddressL2() : "");
      items.add(a.getAddressL3() != null ? a.getAddressL3() : "");
      items.add(a.getAddressL4() != null ? a.getAddressL4() : "");
      items.add(a.getAddressL5() != null ? a.getAddressL5() : "");
      items.add(a.getAddressL6() != null ? a.getAddressL6() : "");
      items.add(a.getInseeCode() != null ? a.getInseeCode() : "");

      csv.writeNext(items.toArray(new String[items.size()]));
      items.clear();
    }
    csv.close();
    LOG.info("{} exported", path);

    return addresses.size();
  }

  @Override
  public Address createAddress(
      String addressL2,
      String addressL3,
      String addressL4,
      String addressL5,
      String addressL6,
      Country addressL7Country) {

    Address address = new Address();
    address.setAddressL2(addressL2);
    address.setAddressL3(addressL3);
    address.setAddressL4(addressL4);
    address.setAddressL5(addressL5);
    address.setAddressL6(addressL6);
    address.setAddressL7Country(addressL7Country);

    return address;
  }

  @Override
  public Address getAddress(
      String addressL2,
      String addressL3,
      String addressL4,
      String addressL5,
      String addressL6,
      Country addressL7Country) {

    return addressRepo
        .all()
        .filter(
            "self.addressL2 = ?1 AND self.addressL3 = ?2 AND self.addressL4 = ?3 "
                + "AND self.addressL5 = ?4 AND self.addressL6 = ?5 AND self.addressL7Country = ?6",
            addressL2,
            addressL3,
            addressL4,
            addressL5,
            addressL6,
            addressL7Country)
        .fetchOne();
  }

  @Override
  public boolean checkAddressUsed(Long addressId) {
    LOG.debug("Address Id to be checked = {}", addressId);
    if (addressId != null) {
      if (partnerRepo
              .all()
              .filter(
                  "self.mainInvoicingAddress.id = ?1 OR self.deliveryAddress.id = ?1", addressId)
              .fetchOne()
          != null) return true;
    }
    return false;
  }

  @Override
  @Transactional
  public Optional<Pair<BigDecimal, BigDecimal>> getOrUpdateLatLong(Address address)
      throws AxelorException, JSONException {
    Preconditions.checkNotNull(address, I18n.get(IExceptionMessage.ADDRESS_CANNOT_BE_NULL));
    Optional<Pair<BigDecimal, BigDecimal>> latLong = getLatLong(address);

    if (latLong.isPresent()) {
      return latLong;
    }

    return updateLatLong(address);
  }

  @Override
  @Transactional
  public Optional<Pair<BigDecimal, BigDecimal>> updateLatLong(Address address)
      throws AxelorException, JSONException {
    Preconditions.checkNotNull(address, I18n.get(IExceptionMessage.ADDRESS_CANNOT_BE_NULL));

    if (mapService.isConfigured() && StringUtils.notBlank(address.getFullName())) {
      Map<String, Object> result = mapService.getMap(address.getFullName());
      BigDecimal latitude = (BigDecimal) result.get("latitude");
      BigDecimal longitude = (BigDecimal) result.get("longitude");
      setLatLong(address, Pair.of(latitude, longitude));
    }

    return getLatLong(address);
  }

  @Override
  @Transactional
  public void resetLatLong(Address address) {
    Preconditions.checkNotNull(address, I18n.get(IExceptionMessage.ADDRESS_CANNOT_BE_NULL));
    setLatLong(address, Pair.of(null, null));
  }

  protected void setLatLong(Address address, Pair<BigDecimal, BigDecimal> latLong) {
    address.setLatit(latLong.getLeft());
    address.setLongit(latLong.getRight());
  }

  protected Optional<Pair<BigDecimal, BigDecimal>> getLatLong(Address address) {
    if (address.getLatit() != null && address.getLongit() != null) {
      return Optional.of(Pair.of(address.getLatit(), address.getLongit()));
    }

    return Optional.empty();
  }

  @Override
  public String computeFullName(Address address) {

    String l2 = address.getAddressL2();
    String l3 = address.getAddressL3();
    String l4 = address.getAddressL4();
    String l5 = address.getAddressL5();
    String l6 = address.getAddressL6();

    return (!Strings.isNullOrEmpty(l2) ? l2 : "")
        + (!Strings.isNullOrEmpty(l3) ? " " + l3 : "")
        + (!Strings.isNullOrEmpty(l4) ? " " + l4 : "")
        + (!Strings.isNullOrEmpty(l5) ? " " + l5 : "")
        + (!Strings.isNullOrEmpty(l6) ? " " + l6 : "");
  }

  @Override
  public String computeAddressStr(Address address) {
    StringBuilder addressString = new StringBuilder();
    if (address == null) {
      return "";
    }

    if (address.getAddressL2() != null) {
      addressString.append(address.getAddressL2()).append("\n");
    }
    if (address.getAddressL3() != null) {
      addressString.append(address.getAddressL3()).append("\n");
    }
    if (address.getAddressL4() != null) {
      addressString.append(address.getAddressL4()).append("\n");
    }
    if (address.getAddressL5() != null) {
      addressString.append(address.getAddressL5()).append("\n");
    }
    if (address.getAddressL6() != null) {
      addressString.append(address.getAddressL6());
    }
    if (address.getAddressL7Country() != null) {
      addressString = addressString.append("\n").append(address.getAddressL7Country().getName());
    }

    return addressString.toString();
  }
}
