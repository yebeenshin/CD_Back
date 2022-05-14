package SilkLoad.controller.Member;

import SilkLoad.SessionConst;
import SilkLoad.dto.MemberFormDto;
import SilkLoad.dto.ProductFormDto;
import SilkLoad.dto.ProductRecordDto;
import SilkLoad.entity.Members;
import SilkLoad.entity.Product;
import SilkLoad.service.MemberService;
import SilkLoad.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.List;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/members/myPage")
public class MyPageController {

    private final MemberService memberService;
    private final ProductService productService;

    @GetMapping("/profile")
    public String MyPage(@ModelAttribute("memberFormDto")MemberFormDto memberFormDto) {

        return "memberProfile";
    }

    @PostMapping("/profile")
    public String MyPageModify(@ModelAttribute("memberFormDto")MemberFormDto memberFormDto, BindingResult bindingResult) {

        Members members = memberService.updatePassword(memberFormDto);
        log.info("members={}",members);
        if ( members == null ) {
            bindingResult.rejectValue("loginId","nonExistence");
        }

        return "memberProfile";
    }

    @GetMapping("/wishlist")
    public String Wishlist(@ModelAttribute("productData") ProductFormDto productData) {
        return "memberWishlist";

    }

    @GetMapping("/orders")
    public String Orders(Model model, HttpServletRequest request) {

        HttpSession session = request.getSession();
        Object memberObject = session.getAttribute(SessionConst.LOGIN_MEMBER);
        Members sessionMember = (Members) memberObject;
        Members member = memberService.findByLoginId( sessionMember.getLoginId());
        List<Product> memberProductList = member.getProductList();
        List<ProductRecordDto> productRecordDtoList = productService.getProductRecordDtoList(memberProductList);
        model.addAttribute("productRecordDtoList", productRecordDtoList);
        log.info("물품리스트={}",productRecordDtoList );



        return "memberOrders";

    }





}